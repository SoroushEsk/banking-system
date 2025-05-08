package com.tabdil_exchange.test_project.features.transaction.controller

import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionDepositResponse
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionRequest
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionWithdrawalResponse
import com.tabdil_exchange.test_project.features.transaction.service.TransactionService
import com.tabdil_exchange.test_project.util.TransactionLogger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.system.measureTimeMillis

@RestController
@RequestMapping("/api/transactions")
class TransactionController(
        private val transactionService: TransactionService,
        private val transactionLogger: TransactionLogger
) {
    @PostMapping("/deposit")
    suspend fun deposit(@RequestBody request: TransactionRequest): ResponseEntity<Any> {
        transactionLogger.logOperation(
                operation = "DEPOSIT_REQUEST",
                status = "START",
                parameters = mapOf(
                        "transactionId" to request.transaction_id,
                        "accountId" to request.account_id,
                        "amount" to request.amount
                )
        )
        return try {
            val response = transactionService.handlingDeposit(request)
            transactionLogger.logOperation(
                    operation = "DEPOSIT_REQUEST",
                    status = "SUCCESS",
                    parameters = mapOf(
                            "transactionId" to request.transaction_id,
                            "accountId" to request.account_id,
                            "newBalance" to response.new_balance
                    ),
                    durationMs = -1
            )
            ResponseEntity.ok(response)
        } catch (e: NumberFormatException) {
            transactionLogger.logDepositError(
                    transactionId = request.transaction_id,
                    accountId = request.account_id,
                    amount = request.amount,
                    errorType = "InvalidAmountFormat",
                    errorMessage = "The provided amount '${request.amount}' is not a valid number."
            )
            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidAmountFormat",
                            message = "The provided amount '${request.amount}' is not a valid number."
                    )
            )
        } catch (e: IllegalArgumentException) {
            transactionLogger.logDepositError(
                    transactionId = request.transaction_id,
                    accountId = request.account_id,
                    amount = request.amount,
                    errorType = "InvalidTransaction",
                    errorMessage = e.message ?: "Invalid deposit operation."
            )
            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidTransaction",
                            message = e.message ?: "Invalid deposit operation."
                    )
            )
        } catch (e: NoSuchElementException) {
            transactionLogger.logDepositError(
                    transactionId = request.transaction_id,
                    accountId = request.account_id,
                    amount = request.amount,
                    errorType = "ResourceNotFound",
                    errorMessage = e.message ?: "Requested resource could not be found."
            )
            ResponseEntity.status(404).body(
                    ErrorResponse(
                            error = "ResourceNotFound",
                            message = e.message ?: "Requested resource could not be found."
                    )
            )
        } catch (e: Exception) {
            transactionLogger.logDepositError(
                    transactionId = request.transaction_id,
                    accountId = request.account_id,
                    amount = request.amount,
                    errorType = "ServerError",
                    errorMessage = "An unexpected error occurred: ${e.message}"
            )
            ResponseEntity.status(500).body(
                    ErrorResponse(
                            error = "ServerError",
                            message = "An unexpected error occurred during deposit. Please try again later."
                    )
            )
        }

    }

    @PostMapping("/withdraw")
    fun withdraw(@RequestBody request: TransactionRequest): ResponseEntity<Any> {
        transactionLogger.logOperation(
                operation = "WITHDRAWAL_REQUEST",
                status = "START",
                parameters = mapOf(
                        "transactionId" to request.transaction_id,
                        "accountId" to request.account_id,
                        "amount" to request.amount
                )
        )
        return try {
            val response = transactionService.handlingWithdrawal(request)
            if (response.status == "completed") {
                transactionLogger.logOperation(
                        operation = "WITHDRAWAL_REQUEST",
                        status = "SUCCESS",
                        parameters = mapOf(
                                "transactionId" to request.transaction_id,
                                "accountId" to request.account_id,
                                "currentBalance" to response.current_balance
                        ),
                        durationMs = -1
                )
            } else if (response.status == "failed" && response.current_balance != "unknown") {
                transactionLogger.logInsufficientFunds(
                        transactionId = request.transaction_id,
                        accountId = request.account_id,
                        requestedAmount = request.amount,
                        currentBalance = response.current_balance
                )
            }
            ResponseEntity.ok(response)
        } catch (e: NumberFormatException) {
            transactionLogger.logWithdrawalError(
                    transactionId = request.transaction_id,
                    accountId = request.account_id,
                    amount = request.amount,
                    errorType = "InvalidAmountFormat",
                    errorMessage = "The provided amount '${request.amount}' is not a valid number."
            )
            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidAmountFormat",
                            message = "The provided amount '${request.amount}' is not a valid number."
                    )
            )
        } catch (e: IllegalArgumentException) {
            transactionLogger.logWithdrawalError(
                    transactionId = request.transaction_id,
                    accountId = request.account_id,
                    amount = request.amount,
                    errorType = "InvalidTransaction",
                    errorMessage = e.message ?: "Invalid withdrawal operation."
            )
            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidTransaction",
                            message = e.message ?: "Invalid withdrawal operation."
                    )
            )
        } catch (e: NoSuchElementException) {
            transactionLogger.logWithdrawalError(
                    transactionId = request.transaction_id,
                    accountId = request.account_id,
                    amount = request.amount,
                    errorType = "ResourceNotFound",
                    errorMessage = e.message ?: "Requested resource could not be found."
            )
            ResponseEntity.status(404).body(
                    ErrorResponse(
                            error = "ResourceNotFound",
                            message = e.message ?: "Requested resource could not be found."
                    )
            )
        } catch (e: Exception) {
            transactionLogger.logWithdrawalError(
                    transactionId = request.transaction_id,
                    accountId = request.account_id,
                    amount = request.amount,
                    errorType = "ServerError",
                    errorMessage = "An unexpected error occurred: ${e.message}"
            )
            ResponseEntity.status(500).body(
                    ErrorResponse(
                            error = "ServerError",
                            message = "An unexpected error occurred during withdrawal. Please try again later."
                    )
            )
        }
    }
}

data class ErrorResponse(
        val error: String,
        val message: String
)