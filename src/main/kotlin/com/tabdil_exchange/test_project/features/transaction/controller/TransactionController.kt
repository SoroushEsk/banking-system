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
        return try {
            var response: TransactionDepositResponse
            val durationMs = measureTimeMillis {
                response = transactionService.handlingDeposit(request)
            }
            transactionLogger.logDepositSuccess(
                    request.transaction_id,
                    request.account_id,
                    request.amount,
                    response.new_balance,
                    durationMs
            )

            ResponseEntity.ok(response)
        } catch (e: NumberFormatException) {
            // Log error
            transactionLogger.logDepositError(
                    request.transaction_id,
                    request.account_id,
                    request.amount,
                    "InvalidAmountFormat",
                    "The provided amount '${request.amount}' is not a valid number."
            )

            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidAmountFormat",
                            message = "The provided amount '${request.amount}' is not a valid number."
                    )
            )
        } catch (e: IllegalArgumentException) {
            // Log error
            transactionLogger.logDepositError(
                    request.transaction_id,
                    request.account_id,
                    request.amount,
                    "InvalidTransaction",
                    e.message ?: "Invalid deposit operation."
            )

            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidTransaction",
                            message = e.message ?: "Invalid deposit operation."
                    )
            )
        } catch (e: NoSuchElementException) {
            // Log error
            transactionLogger.logDepositError(
                    request.transaction_id,
                    request.account_id,
                    request.amount,
                    "ResourceNotFound",
                    e.message ?: "Requested resource could not be found."
            )

            ResponseEntity.status(404).body(
                    ErrorResponse(
                            error = "ResourceNotFound",
                            message = e.message ?: "Requested resource could not be found."
                    )
            )
        } catch (e: Exception) {
            // Log error
            transactionLogger.logDepositError(
                    request.transaction_id,
                    request.account_id,
                    request.amount,
                    "ServerError",
                    "An unexpected error occurred: ${e.message}"
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
    suspend fun withdraw(@RequestBody request: TransactionRequest): ResponseEntity<Any> {
        return try {
            var response: TransactionWithdrawalResponse
            val durationMs = measureTimeMillis {
                response = transactionService.handlingWithdrawal(request)
            }

            // Log successful withdrawal
            transactionLogger.logWithdrawalSuccess(
                    request.transaction_id,
                    request.account_id,
                    request.amount,
                    response.current_balance,
                    durationMs
            )

            ResponseEntity.ok(response)
        } catch (e: NumberFormatException) {
            // Log error
            transactionLogger.logWithdrawalError(
                    request.transaction_id,
                    request.account_id,
                    request.amount,
                    "InvalidAmountFormat",
                    "The provided amount '${request.amount}' is not a valid number."
            )

            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidAmountFormat",
                            message = "The provided amount '${request.amount}' is not a valid number."
                    )
            )
        } catch (e: IllegalArgumentException) {
            if (e.message?.contains("insufficient", ignoreCase = true) == true) {
                val currentBalance = e.message?.let {
                    val match = Regex("current balance: ([\\d.]+)").find(it)
                    match?.groupValues?.get(1) ?: "0.00"
                } ?: "0.00"

                transactionLogger.logInsufficientFunds(
                        request.transaction_id,
                        request.account_id,
                        request.amount,
                        currentBalance
                )
            } else {
                transactionLogger.logWithdrawalError(
                        request.transaction_id,
                        request.account_id,
                        request.amount,
                        "InvalidTransaction",
                        e.message ?: "Invalid withdrawal operation."
                )
            }

            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidTransaction",
                            message = e.message ?: "Invalid withdrawal operation."
                    )
            )
        } catch (e: NoSuchElementException) {
            // Log error
            transactionLogger.logWithdrawalError(
                    request.transaction_id,
                    request.account_id,
                    request.amount,
                    "ResourceNotFound",
                    e.message ?: "Requested resource could not be found."
            )

            ResponseEntity.status(404).body(
                    ErrorResponse(
                            error = "ResourceNotFound",
                            message = e.message ?: "Requested resource could not be found."
                    )
            )
        } catch (e: Exception) {
            transactionLogger.logWithdrawalError(
                    request.transaction_id,
                    request.account_id,
                    request.amount,
                    "ServerError",
                    "An unexpected error occurred: ${e.message}"
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