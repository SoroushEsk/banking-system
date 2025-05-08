package com.tabdil_exchange.test_project.features.transaction.service

import com.tabdil_exchange.test_project.features.account.model.Account
import com.tabdil_exchange.test_project.features.account.repository.AccountRepository
import com.tabdil_exchange.test_project.features.transaction.model.Transaction
import com.tabdil_exchange.test_project.features.transaction.model.TransactionStatus
import com.tabdil_exchange.test_project.features.transaction.model.TransactionType
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionDepositResponse
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionRequest
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionWithdrawalResponse
import com.tabdil_exchange.test_project.features.transaction.repository.TransactionRepository
import com.tabdil_exchange.test_project.util.TransactionLogger
import jakarta.transaction.Transactional
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class TransactionService(
        private val accountRepository: AccountRepository,
        private val transactionRepository: TransactionRepository,
        private val transactionLogger: TransactionLogger
) {
    private val logger = LoggerFactory.getLogger(TransactionService::class.java)

    @Async
    @Transactional
    fun handlingDeposit(transactionRequest: TransactionRequest): TransactionDepositResponse {
        if (transactionRepository.existsById(transactionRequest.transaction_id)) {
            val existingTransaction = transactionRepository.findById(transactionRequest.transaction_id).orElseThrow {
                NoSuchElementException("Transaction not found ${transactionRequest.transaction_id}")
            }
            transactionLogger.logOperation(
                    operation = "CHECK_TRANSACTION_EXISTS",
                    status = "SUCCESS",
                    parameters = mapOf("transactionId" to transactionRequest.transaction_id)
            )
            return createTransactionDepositResponse(existingTransaction)
        }

        val amount = transactionRequest.amount.toDoubleOrNull()
                ?: throw NumberFormatException("Invalid amount format ${transactionRequest.amount}")
        val transactionId = transactionRequest.transaction_id.toLongOrNull()
                ?: throw NumberFormatException("Invalid transaction id format ${transactionRequest.transaction_id}")
        val accountId = transactionRequest.account_id.toLongOrNull()
                ?: throw NumberFormatException("Invalid account id format ${transactionRequest.account_id}")

        transactionLogger.logOperation(
                operation = "PARSE_DEPOSIT_REQUEST",
                status = "SUCCESS",
                parameters = mapOf(
                        "transactionId" to transactionId.toString(),
                        "accountId" to accountId.toString(),
                        "amount" to amount.toString()
                )
        )

        val account = accountRepository.findById(accountId).orElseGet {
            val newAccount = Account(accountId = accountId, balance = 0.0)
            accountRepository.save(newAccount)
            transactionLogger.logOperation(
                    operation = "CREATE_ACCOUNT",
                    status = "SUCCESS",
                    parameters = mapOf("accountId" to accountId.toString())
            )
            newAccount
        }

        val transaction = Transaction(
                transactionId = transactionId,
                accountId = account.accountId,
                amount = amount,
                status = TransactionStatus.PENDING,
                type = TransactionType.DEPOSIT
        )
        transactionRepository.save(transaction)
        transactionLogger.logOperation(
                operation = "SAVE_TRANSACTION",
                status = "SUCCESS",
                parameters = mapOf("transactionId" to transactionId.toString())
        )

        try {
            val newBalance = account.deposit(amount)
            accountRepository.save(account)
            transaction.status = TransactionStatus.COMPLETED
            transactionRepository.save(transaction)
            transactionLogger.logDepositSuccess(
                    transactionId = transactionId.toString(),
                    accountId = accountId.toString(),
                    amount = amount.toString(),
                    newBalance = newBalance.toString(),
                    durationMs = -1
            )
            return TransactionDepositResponse(
                    transaction_id = transaction.transactionId.toString(),
                    account_id = transaction.accountId.toString(),
                    new_balance = newBalance.toString(),
                    status = "completed"
            )
        } catch (e: Exception) {
            logger.error("Deposit failed for transaction ${transactionRequest.transaction_id}", e)
            val transactionId = transactionRequest.transaction_id.toLongOrNull() ?: transactionRequest.transaction_id
            val accountId = transactionRequest.account_id.toLongOrNull() ?: transactionRequest.account_id
            if (transactionRepository.existsById(transactionRequest.transaction_id)) {
                val transaction = transactionRepository.findById(transactionRequest.transaction_id).orElseThrow()
                transaction.status = TransactionStatus.FAILED
                transaction.failureReason = e.message
                transactionRepository.save(transaction)
            }
            transactionLogger.logDepositError(
                    transactionId = transactionId.toString(),
                    accountId = accountId.toString(),
                    amount = transactionRequest.amount,
                    errorType = e.javaClass.simpleName,
                    errorMessage = e.message ?: "Unknown error"
            )
            throw e
        }
    }

    @Async
    @Transactional
    fun handlingWithdrawal(transactionRequest: TransactionRequest): TransactionWithdrawalResponse {
        val maxAttempts = 3
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                transactionLogger.logOperation(
                        operation = "ATTEMPT_WITHDRAWAL",
                        status = "START",
                        parameters = mapOf(
                                "transactionId" to transactionRequest.transaction_id,
                                "accountId" to transactionRequest.account_id,
                                "amount" to transactionRequest.amount,
                                "attempt" to attempt.toString()
                        )
                )
                return processWithdrawal(transactionRequest)
            } catch (e: OptimisticLockingFailureException) {
                logger.warn("Optimistic locking failure for transaction ${transactionRequest.transaction_id}, attempt $attempt", e)
                transactionLogger.logWithdrawalError(
                        transactionId = transactionRequest.transaction_id,
                        accountId = transactionRequest.account_id,
                        amount = transactionRequest.amount,
                        errorType = "OptimisticLockingFailure",
                        errorMessage = "Locking failure on attempt $attempt: ${e.message}"
                )
                lastException = e
                if (attempt == maxAttempts) {
                    transactionLogger.logOperation(
                            operation = "WITHDRAWAL",
                            status = "FAILURE",
                            parameters = mapOf(
                                    "transactionId" to transactionRequest.transaction_id,
                                    "accountId" to transactionRequest.account_id,
                                    "amount" to transactionRequest.amount
                            ),
                            reason = "Failed after $maxAttempts attempts: ${e.message}"
                    )
                }
            } catch (e: PessimisticLockingFailureException) {
                logger.warn("Pessimistic locking failure for transaction ${transactionRequest.transaction_id}, attempt $attempt", e)
                transactionLogger.logWithdrawalError(
                        transactionId = transactionRequest.transaction_id,
                        accountId = transactionRequest.account_id,
                        amount = transactionRequest.amount,
                        errorType = "PessimisticLockingFailure",
                        errorMessage = "Locking failure on attempt $attempt: ${e.message}"
                )
                lastException = e
                if (attempt == maxAttempts) {
                    transactionLogger.logOperation(
                            operation = "WITHDRAWAL",
                            status = "FAILURE",
                            parameters = mapOf(
                                    "transactionId" to transactionRequest.transaction_id,
                                    "accountId" to transactionRequest.account_id,
                                    "amount" to transactionRequest.amount
                            ),
                            reason = "Failed after $maxAttempts attempts: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                logger.error("Withdrawal failed for transaction ${transactionRequest.transaction_id}", e)
                transactionLogger.logWithdrawalError(
                        transactionId = transactionRequest.transaction_id,
                        accountId = transactionRequest.account_id,
                        amount = transactionRequest.amount,
                        errorType = e.javaClass.simpleName,
                        errorMessage = e.message ?: "Unknown error"
                )
                throw e
            }
            Thread.sleep(200 * attempt.toLong())
        }
        throw lastException ?: IllegalStateException("Withdrawal failed after $maxAttempts attempts")
    }

    fun processWithdrawal(transactionRequest: TransactionRequest): TransactionWithdrawalResponse {
        if (transactionRepository.existsById(transactionRequest.transaction_id)) {
            val existingTransaction = transactionRepository.findById(transactionRequest.transaction_id).orElseThrow {
                NoSuchElementException("Transaction not found ${transactionRequest.transaction_id}")
            }
            transactionLogger.logOperation(
                    operation = "CHECK_TRANSACTION_EXISTS",
                    status = "SUCCESS",
                    parameters = mapOf("transactionId" to transactionRequest.transaction_id)
            )
            return createTransactionWithdrawalResponse(existingTransaction)
        }

        val amount = transactionRequest.amount.toDoubleOrNull()
                ?: throw NumberFormatException("Invalid amount format ${transactionRequest.amount}")
        val transactionId = transactionRequest.transaction_id.toLongOrNull()
                ?: throw NumberFormatException("Invalid transaction id format ${transactionRequest.transaction_id}")
        val accountId = transactionRequest.account_id.toLongOrNull()
                ?: throw NumberFormatException("Invalid account id format ${transactionRequest.account_id}")

        transactionLogger.logOperation(
                operation = "PARSE_WITHDRAWAL_REQUEST",
                status = "SUCCESS",
                parameters = mapOf(
                        "transactionId" to transactionId.toString(),
                        "accountId" to accountId.toString(),
                        "amount" to amount.toString()
                )
        )

        val account = findAccountWithLock(accountId)
                ?: throw NoSuchElementException("Account not found $accountId")

        val transaction = Transaction(
                transactionId = transactionId,
                accountId = account.accountId,
                amount = amount,
                status = TransactionStatus.PENDING,
                type = TransactionType.WITHDRAWAL
        )
        transactionRepository.save(transaction)
        transactionLogger.logOperation(
                operation = "SAVE_TRANSACTION",
                status = "SUCCESS",
                parameters = mapOf("transactionId" to transactionId.toString())
        )

        try {
            if (account.balance < amount) {
                transaction.status = TransactionStatus.FAILED
                transaction.failureReason = "Insufficient funds"
                transactionRepository.save(transaction)
                transactionLogger.logInsufficientFunds(
                        transactionId = transactionId.toString(),
                        accountId = accountId.toString(),
                        requestedAmount = amount.toString(),
                        currentBalance = account.balance.toString()
                )
                return TransactionWithdrawalResponse(
                        transaction_id = transaction.transactionId.toString(),
                        account_id = transaction.accountId.toString(),
                        current_balance = account.balance.toString(),
                        requested_amount = amount.toString(),
                        status = "failed"
                )
            }

            val newBalance = account.withdraw(amount)
            accountRepository.save(account)
            transaction.status = TransactionStatus.COMPLETED
            transactionRepository.save(transaction)
            transactionLogger.logWithdrawalSuccess(
                    transactionId = transactionId.toString(),
                    accountId = accountId.toString(),
                    amount = amount.toString(),
                    newBalance = newBalance.toString(),
                    durationMs = -1
            )
            return TransactionWithdrawalResponse(
                    transaction_id = transaction.transactionId.toString(),
                    account_id = transaction.accountId.toString(),
                    current_balance = newBalance.toString(),
                    requested_amount = amount.toString(),
                    status = "completed"
            )
        } catch (e: Exception) {
            logger.error("Withdrawal processing failed for transaction $transactionId", e)
            transaction.status = TransactionStatus.FAILED
            transaction.failureReason = e.message
            transactionRepository.save(transaction)
            transactionLogger.logWithdrawalError(
                    transactionId = transactionId.toString(),
                    accountId = accountId.toString(),
                    amount = amount.toString(),
                    errorType = e.javaClass.simpleName,
                    errorMessage = e.message ?: "Unknown error"
            )
            throw e
        }
    }

    private fun findAccountWithLock(accountId: Long): Account? {
        return try {
            val account = accountRepository.findByIdLocked(accountId)
            transactionLogger.logOperation(
                    operation = "FIND_ACCOUNT_LOCKED",
                    status = if (account != null) "SUCCESS" else "FAILURE",
                    parameters = mapOf("accountId" to accountId.toString()),
                    reason = if (account == null) "Account not found" else null
            )
            return account
        } catch (e: Exception) {
            logger.warn("Error getting account with lock for account $accountId", e)
            transactionLogger.logOperation(
                    operation = "FIND_ACCOUNT_LOCKED",
                    status = "FAILURE",
                    parameters = mapOf("accountId" to accountId.toString()),
                    reason = "${e.javaClass.simpleName}: ${e.message}"
            )
            return null
        }
    }

    private fun createTransactionWithdrawalResponse(transaction: Transaction): TransactionWithdrawalResponse {
        return when (transaction.status) {
            TransactionStatus.COMPLETED -> {
                val account = accountRepository.findById(transaction.accountId).orElseThrow()
                transactionLogger.logOperation(
                        operation = "FETCH_ACCOUNT",
                        status = "SUCCESS",
                        parameters = mapOf("accountId" to transaction.accountId.toString())
                )
                TransactionWithdrawalResponse(
                        transaction_id = transaction.transactionId.toString(),
                        account_id = transaction.accountId.toString(),
                        current_balance = account.balance.toString(),
                        requested_amount = transaction.amount.toString(),
                        status = "completed"
                )
            }

            TransactionStatus.FAILED -> {
                val account = accountRepository.findById(transaction.accountId).orElseThrow()
                transactionLogger.logOperation(
                        operation = "FETCH_ACCOUNT",
                        status = "SUCCESS",
                        parameters = mapOf("accountId" to transaction.accountId.toString())
                )
                TransactionWithdrawalResponse(
                        transaction_id = transaction.transactionId.toString(),
                        account_id = transaction.accountId.toString(),
                        current_balance = account.balance.toString(),
                        requested_amount = transaction.amount.toString(),
                        status = "failed"
                )
            }

            else -> {
                transactionLogger.logOperation(
                        operation = "FETCH_TRANSACTION",
                        status = "PENDING",
                        parameters = mapOf("transactionId" to transaction.transactionId.toString())
                )
                TransactionWithdrawalResponse(
                        transaction_id = transaction.transactionId.toString(),
                        account_id = transaction.accountId.toString(),
                        current_balance = "unknown",
                        requested_amount = transaction.amount.toString(),
                        status = "pending"
                )
            }
        }
    }

    private fun createTransactionDepositResponse(transaction: Transaction): TransactionDepositResponse {
        return when (transaction.status) {
            TransactionStatus.COMPLETED -> {
                val account = accountRepository.findById(transaction.accountId).orElseThrow()
                transactionLogger.logOperation(
                        operation = "FETCH_ACCOUNT",
                        status = "SUCCESS",
                        parameters = mapOf("accountId" to transaction.accountId.toString())
                )
                TransactionDepositResponse(
                        transaction_id = transaction.transactionId.toString(),
                        account_id = transaction.accountId.toString(),
                        new_balance = account.balance.toString(),
                        status = "completed"
                )
            }

            TransactionStatus.FAILED -> {
                val account = accountRepository.findById(transaction.accountId).orElseThrow()
                transactionLogger.logOperation(
                        operation = "FETCH_ACCOUNT",
                        status = "SUCCESS",
                        parameters = mapOf("accountId" to transaction.accountId.toString())
                )
                TransactionDepositResponse(
                        transaction_id = transaction.transactionId.toString(),
                        account_id = transaction.accountId.toString(),
                        new_balance = account.balance.toString(),
                        status = "failed"
                )
            }

            else -> {
                transactionLogger.logOperation(
                        operation = "FETCH_TRANSACTION",
                        status = "PENDING",
                        parameters = mapOf("transactionId" to transaction.transactionId.toString())
                )
                TransactionDepositResponse(
                        transaction_id = transaction.transactionId.toString(),
                        account_id = transaction.accountId.toString(),
                        new_balance = "unknown",
                        status = "pending"
                )
            }
        }
    }
}