package com.tabdil_exchange.test_project.features.transaction.service

import com.tabdil_exchange.test_project.features.account.model.Account
import com.tabdil_exchange.test_project.features.account.repository.AccountRepository
import com.tabdil_exchange.test_project.features.transaction.model.Transaction
import com.tabdil_exchange.test_project.features.transaction.model.TransactionStatus
import com.tabdil_exchange.test_project.features.transaction.model.TransactionType
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionRequest
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionDepositResponse
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionWithdrawalResponse
import com.tabdil_exchange.test_project.features.transaction.repository.TransactionRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class TransactionService(
        private val accountRepository: AccountRepository,
        private val transactionRepository: TransactionRepository
) {
    @Transactional
    fun handlingDeposit(transactionRequest: TransactionRequest): TransactionDepositResponse{
        if(transactionRepository.existsById(transactionRequest.transaction_id)){
            val existingTransaction = transactionRepository.findById(transactionRequest.transaction_id).orElseThrow {
                NoSuchElementException("Transaction not found ${transactionRequest.transaction_id}")
            }
            return createTransactionDepositResponse(existingTransaction)
        }
        val amount = transactionRequest.amount.toDoubleOrNull() ?: throw NumberFormatException("Wrong number format ${transactionRequest.amount}")
        val transactionId = transactionRequest.transaction_id.toLongOrNull() ?: throw NumberFormatException("Wrong transaction id format ${transactionRequest.transaction_id}")
        val accountId = transactionRequest.account_id.toLongOrNull() ?: throw NumberFormatException("Wrong account id format ${transactionRequest.transaction_id}")
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
        try{
            val newBalance = account.deposit(transactionRequest.amount.toDouble())
            accountRepository.save(account)
            transaction.status = TransactionStatus.COMPLETED
            transactionRepository.save(transaction)
            return TransactionDepositResponse(
                    transaction_id = transaction.transactionId.toString(),
                    account_id = transaction.accountId.toString(),
                    new_balance = newBalance.toString(),
                    status = "completed"
            )
        }catch (e: Exception){
            transaction.status = TransactionStatus.FAILED
            transaction.failureReason = e.message
            transactionRepository.save(transaction)
            throw e
        }

    }


    @Transactional
    fun handlingWithdrawal(transactionRequest: TransactionRequest): TransactionWithdrawalResponse {
        if(transactionRepository.existsById(transactionRequest.transaction_id)){
            val existingTransaction = transactionRepository.findById(transactionRequest.transaction_id).orElseThrow{
                NoSuchElementException("Transaction not found ${transactionRequest.transaction_id}")
            }
            return createTransactionWithdrawalResponse(existingTransaction)
        }
        val amount = transactionRequest.amount.toDoubleOrNull() ?: throw NumberFormatException("Wrong number format ${transactionRequest.amount}")
        val transactionId = transactionRequest.transaction_id.toLongOrNull() ?: throw NumberFormatException("Wrong transaction id format ${transactionRequest.transaction_id}")
        val accountId = transactionRequest.account_id.toLongOrNull() ?: throw NumberFormatException("Wrong account id format ${transactionRequest.transaction_id}")


        val account = accountRepository.findById(accountId)
                .orElseThrow{NoSuchElementException("Account not found ${transactionRequest.account_id}")}
        val transaction = Transaction(
                transactionId = transactionId,
                accountId = account.accountId,
                amount = amount,
                status = TransactionStatus.PENDING,
                type = TransactionType.DEPOSIT
        )

        transactionRepository.save(transaction)
        try{
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
        }catch (e: Exception){
            transaction.status = TransactionStatus.FAILED
            transaction.failureReason = e.message
            transactionRepository.save(transaction)
            throw e
        }

    }
    private fun createTransactionWithdrawalResponse(transaction: Transaction):TransactionWithdrawalResponse{
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
    private fun createTransactionDepositResponse(transaction: Transaction):TransactionDepositResponse{
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