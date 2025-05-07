package com.tabdil_exchange.test_project.features.transaction.controller

import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionDepositResponse
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionRequest
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionWithdrawalResponse
import com.tabdil_exchange.test_project.features.transaction.service.TransactionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/transactions")
class TransactionController (
        private val transactionService: TransactionService
){
    @PostMapping("/deposit")
    fun deposit(@RequestBody request: TransactionRequest): ResponseEntity<Any> {
        return try {
            val response = transactionService.handlingDeposit(request)
            ResponseEntity.ok(response)
        } catch (e: NumberFormatException) {
            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidAmountFormat",
                            message = "The provided amount '${request.amount}' is not a valid number."
                    )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidTransaction",
                            message = e.message ?: "Invalid deposit operation."
                    )
            )
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(404).body(
                    ErrorResponse(
                            error = "ResourceNotFound",
                            message = e.message ?: "Requested resource could not be found."
                    )
            )
        } catch (e: Exception) {
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
        return try {
            val response = transactionService.handlingWithdrawal(request)
            ResponseEntity.ok(response)
        } catch (e: NumberFormatException) {
            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidAmountFormat",
                            message = "The provided amount '${request.amount}' is not a valid number."
                    )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                    ErrorResponse(
                            error = "InvalidTransaction",
                            message = e.message ?: "Invalid withdrawal operation."
                    )
            )
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(404).body(
                    ErrorResponse(
                            error = "ResourceNotFound",
                            message = e.message ?: "Requested resource could not be found."
                    )
            )
        } catch (e: Exception) {
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
