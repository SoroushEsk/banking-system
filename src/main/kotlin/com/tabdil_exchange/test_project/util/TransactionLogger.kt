package com.tabdil_exchange.test_project.util

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class TransactionLogger {
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val logger = LoggerFactory.getLogger(TransactionLogger::class.java)
    private val monitorLogger = LoggerFactory.getLogger("TRANSACTION_MONITOR")
    private var depositCount = 0
    private var withdrawalCount = 0
    private var depositErrorCount = 0
    private var withdrawalErrorCount = 0
    private var insufficientFundsCount = 0


    fun logDepositSuccess(transactionId: String, accountId: String, amount: String, newBalance: String, durationMs: Long) {
        depositCount++
        val timestamp = LocalDateTime.now().format(timestampFormat)

        monitorLogger.info("[DEPOSIT_SUCCESS] timestamp={}, transactionId={}, accountId={}, amount={}, newBalance={}, durationMs={}",
                timestamp, transactionId, accountId, amount, newBalance, durationMs)

        checkAndLogStats()

    }
    fun logWithdrawalSuccess(transactionId: String, accountId: String, amount: String, newBalance: String, durationMs: Long) {
        withdrawalCount++
        val timestamp = LocalDateTime.now().format(timestampFormat)

        monitorLogger.info("[WITHDRAWAL_SUCCESS] timestamp={}, transactionId={}, accountId={}, amount={}, newBalance={}, durationMs={}",
                timestamp, transactionId, accountId, amount, newBalance, durationMs)

        checkAndLogStats()
    }
    fun logDepositError(transactionId: String, accountId: String, amount: String, errorType: String, errorMessage: String) {
        depositErrorCount++
        val timestamp = LocalDateTime.now().format(timestampFormat)

        monitorLogger.error("[DEPOSIT_ERROR] timestamp={}, transactionId={}, accountId={}, amount={}, errorType={}, message={}",
                timestamp, transactionId, accountId, amount, errorType, errorMessage)
    }

    fun logWithdrawalError(transactionId: String, accountId: String, amount: String, errorType: String, errorMessage: String) {
        withdrawalErrorCount++
        val timestamp = LocalDateTime.now().format(timestampFormat)

        monitorLogger.error("[WITHDRAWAL_ERROR] timestamp={}, transactionId={}, accountId={}, amount={}, errorType={}, message={}",
                timestamp, transactionId, accountId, amount, errorType, errorMessage)
    }

    fun logInsufficientFunds(transactionId: String, accountId: String, requestedAmount: String, currentBalance: String) {
        insufficientFundsCount++
        withdrawalErrorCount++ // Also count as a withdrawal error
        val timestamp = LocalDateTime.now().format(timestampFormat)

        monitorLogger.warn("[INSUFFICIENT_FUNDS] timestamp={}, transactionId={}, accountId={}, requestedAmount={}, currentBalance={}",
                timestamp, transactionId, accountId, requestedAmount, currentBalance)
    }

    fun logBalanceCheck(accountId: Long, isConsistent: Boolean, storedBalance: BigDecimal?, calculatedBalance: BigDecimal?) {
        val timestamp = LocalDateTime.now().format(timestampFormat)

        if (isConsistent) {
            monitorLogger.info("[BALANCE_CHECK_SUCCESS] timestamp={}, accountId={}, balance={}",
                    timestamp, accountId, storedBalance)
        } else {
            monitorLogger.error("[BALANCE_CHECK_FAILED] timestamp={}, accountId={}, storedBalance={}, calculatedBalance={}",
                    timestamp, accountId, storedBalance, calculatedBalance)
        }
    }
    private fun checkAndLogStats() {
        val totalTransactions = depositCount + withdrawalCount
        if (totalTransactions % 100 == 0) {
            val errorRate = if (totalTransactions > 0) {
                (depositErrorCount + withdrawalErrorCount).toDouble() / totalTransactions
            } else {
                0.0
            }

            monitorLogger.info("[TRANSACTION_STATS] totalTransactions={}, deposits={}, withdrawals={}, " +
                    "depositErrors={}, withdrawalErrors={}, insufficientFunds={}, errorRate={}",
                    totalTransactions, depositCount, withdrawalCount,
                    depositErrorCount, withdrawalErrorCount, insufficientFundsCount,
                    String.format("%.2f%%", errorRate * 100))
        }
    }
    fun resetCounters() {
        monitorLogger.info("[COUNTER_RESET] deposits={}, withdrawals={}, depositErrors={}, withdrawalErrors={}, insufficientFunds={}",
                depositCount, withdrawalCount, depositErrorCount, withdrawalErrorCount, insufficientFundsCount)

        depositCount = 0
        withdrawalCount = 0
        depositErrorCount = 0
        withdrawalErrorCount = 0
        insufficientFundsCount = 0
    }
}