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
import jakarta.transaction.Transactional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class TransactionService(
        private val accountRepository: AccountRepository,
        private val transactionRepository: TransactionRepository
) {
    private val logger = LoggerFactory.getLogger(TransactionService::class.java)
    @Async
    @Transactional()
    suspend fun handlingDeposit(transactionRequest: TransactionRequest): TransactionDepositResponse = withContext(Dispatchers.IO) {
        if (transactionRepository.existsById(transactionRequest.transaction_id)) {
            val existingTransaction = transactionRepository.findById(transactionRequest.transaction_id).orElseThrow {
                NoSuchElementException("Transaction not found ${transactionRequest.transaction_id}")
            }
            return@withContext createTransactionDepositResponse(existingTransaction)
        }
        val amount = transactionRequest.amount.toDoubleOrNull()
                ?: throw NumberFormatException("Wrong number format ${transactionRequest.amount}")
        val transactionId = transactionRequest.transaction_id.toLongOrNull()
                ?: throw NumberFormatException("Wrong transaction id format ${transactionRequest.transaction_id}")
        val accountId = transactionRequest.account_id.toLongOrNull()
                ?: throw NumberFormatException("Wrong account id format ${transactionRequest.account_id}")

        val account = accountRepository.findById(accountId).orElseGet {
            val newAccount = Account(accountId = accountId, balance = 0.0)
            accountRepository.save(newAccount)
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

        try {
            val newBalance = account.deposit(amount)
            accountRepository.save(account)
            transaction.status = TransactionStatus.COMPLETED
            transactionRepository.save(transaction)
            return@withContext TransactionDepositResponse(
                    transaction_id = transaction.transactionId.toString(),
                    account_id = transaction.accountId.toString(),
                    new_balance = newBalance.toString(),
                    status = "completed"
            )
        } catch (e: Exception) {
            logger.error("Deposit failed: ${e.message}", e)
            transaction.status = TransactionStatus.FAILED
            transaction.failureReason = e.message
            transactionRepository.save(transaction)

            throw e
        }
    }
    @Async
    @Transactional()
    fun handlingWithdrawal(transactionRequest: TransactionRequest): TransactionWithdrawalResponse {
        var lastException: Exception? = null

        try {
            return processWithdrawal(transactionRequest)
        } catch (e: OptimisticLockingFailureException) {
            logger.warn("Optimistic locking failure on attempt ${e.message}")
            lastException = e
        } catch (e: PessimisticLockingFailureException) {
            logger.warn("Pessimistic locking failure on attempt  ${e.message}")
            lastException = e
        } catch (e: Exception) {
            logger.error("Withdrawal failed: ${e.message}", e)
            throw e
        }


        throw lastException ?: IllegalStateException("Withdrawal failed after 3 attempts")
    }
    private fun processWithdrawal(transactionRequest: TransactionRequest): TransactionWithdrawalResponse {
        if (transactionRepository.existsById(transactionRequest.transaction_id)) {
            val existingTransaction = transactionRepository.findById(transactionRequest.transaction_id).orElseThrow {
                NoSuchElementException("Transaction not found ${transactionRequest.transaction_id}")
            }
            return createTransactionWithdrawalResponse(existingTransaction)
        }

        val amount = transactionRequest.amount.toDoubleOrNull()
                ?: throw NumberFormatException("Wrong number format ${transactionRequest.amount}")
        val transactionId = transactionRequest.transaction_id.toLongOrNull()
                ?: throw NumberFormatException("Wrong transaction id format ${transactionRequest.transaction_id}")
        val accountId = transactionRequest.account_id.toLongOrNull()
                ?: throw NumberFormatException("Wrong account id format ${transactionRequest.account_id}")

        val account = findAccountWithLock(accountId)
                ?: throw NoSuchElementException("Account not found ${accountId}")

        val transaction = Transaction(
                transactionId = transactionId,
                accountId = account.accountId,
                amount = amount,
                status = TransactionStatus.PENDING,
                type = TransactionType.WITHDRAWAL
        )
        transactionRepository.save(transaction)

        try {
            if (account.balance < amount) {
                transaction.status = TransactionStatus.FAILED
                transaction.failureReason = "Insufficient funds"
                transactionRepository.save(transaction)

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

            return TransactionWithdrawalResponse(
                    transaction_id = transaction.transactionId.toString(),
                    account_id = transaction.accountId.toString(),
                    current_balance = newBalance.toString(),
                    requested_amount = amount.toString(),
                    status = "completed"
            )
        } catch (e: Exception) {
            logger.error("Withdrawal processing failed: ${e.message}", e)

            transaction.status = TransactionStatus.FAILED
            transaction.failureReason = e.message
            transactionRepository.save(transaction)

            throw e
        }
    }

    private  fun findAccountWithLock(accountId: Long): Account? {
        return try {
            accountRepository.findById(accountId).orElse(null)
        } catch (e: Exception) {
            logger.warn("Error getting account with lock: ${e.message}")
            null
        }
    }
    private  fun createTransactionWithdrawalResponse(transaction: Transaction): TransactionWithdrawalResponse  {
        return when (transaction.status) {
            TransactionStatus.COMPLETED -> {
                val account = accountRepository.findById(transaction.accountId).orElseThrow()
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
                TransactionWithdrawalResponse(
                        transaction_id = transaction.transactionId.toString(),
                        account_id = transaction.accountId.toString(),
                        current_balance = account.balance.toString(),
                        requested_amount = transaction.amount.toString(),
                        status = "failed"
                )
            }
            else -> TransactionWithdrawalResponse(
                    transaction_id = transaction.transactionId.toString(),
                    account_id = transaction.accountId.toString(),
                    current_balance = "unknown",
                    requested_amount = transaction.amount.toString(),
                    status = "pending"
            )
        }
    }
    private  fun createTransactionDepositResponse(transaction: Transaction): TransactionDepositResponse{
        return when (transaction.status) {
            TransactionStatus.COMPLETED -> {
                val account = accountRepository.findById(transaction.accountId).orElseThrow()
                TransactionDepositResponse(
                        transaction_id = transaction.transactionId.toString(),
                        account_id = transaction.accountId.toString(),
                        new_balance = account.balance.toString(),
                        status = "completed"
                )
            }
            TransactionStatus.FAILED -> {
                val account = accountRepository.findById(transaction.accountId).orElseThrow()
                TransactionDepositResponse(
                        transaction_id = transaction.transactionId.toString(),
                        account_id = transaction.accountId.toString(),
                        new_balance = account.balance.toString(),
                        status = "failed"
                )
            }
            else -> TransactionDepositResponse(
                    transaction_id = transaction.transactionId.toString(),
                    account_id = transaction.accountId.toString(),
                    new_balance = "unknown",
                    status = "pending"
            )
        }
    }
}