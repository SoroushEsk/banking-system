package com.tabdil_exchange.test_project.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

@Component
@EnableScheduling
class TransactionLogger(
        private val objectMapper: ObjectMapper
) {
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val logger = LoggerFactory.getLogger(TransactionLogger::class.java)
    private val monitorLogger = LoggerFactory.getLogger("TRANSACTION_MONITOR")

    private var depositCount = AtomicLong(0)
    private var withdrawalCount = AtomicLong(0)
    private var depositErrorCount = AtomicLong(0)
    private var withdrawalErrorCount = AtomicLong(0)
    private var insufficientFundsCount = AtomicLong(0)
    private val transactionsInWindow = AtomicLong(0)
    private val windowStart = AtomicLong(System.currentTimeMillis())

    private val depositDurations = AtomicLong(0)
    private val withdrawalDurations = AtomicLong(0)

    fun logOperation(
            operation: String,
            status: String,
            parameters: Map<String, String?> = emptyMap(),
            durationMs: Long? = null,
            reason: String? = null
    ) {
        val timestamp = LocalDateTime.now().format(timestampFormat)
        val logEntry = mutableMapOf(
                "event" to "OPERATION",
                "timestamp" to timestamp,
                "operation" to operation,
                "status" to status
        )

        if (parameters.isNotEmpty()) logEntry["parameters"] = parameters.toString()
        durationMs?.let { logEntry["durationMs"] = it.toString() }
        reason?.let { logEntry["reason"] = it }

        when (status) {
            "SUCCESS" -> monitorLogger.info(objectMapper.writeValueAsString(logEntry))
            "FAILURE" -> monitorLogger.error(objectMapper.writeValueAsString(logEntry))
            else -> monitorLogger.warn(objectMapper.writeValueAsString(logEntry))
        }
    }

    fun logDepositSuccess(
            transactionId: String,
            accountId: String,
            amount: String,
            newBalance: String,
            durationMs: Long
    ) {
        depositCount.incrementAndGet()
        transactionsInWindow.incrementAndGet()
        depositDurations.addAndGet(durationMs)

        logOperation(
                operation = "DEPOSIT",
                status = "SUCCESS",
                parameters = mapOf(
                        "transactionId" to transactionId,
                        "accountId" to accountId,
                        "amount" to amount,
                        "newBalance" to newBalance
                ),
                durationMs = durationMs
        )
        checkAndLogStats()
    }

    fun logWithdrawalSuccess(
            transactionId: String,
            accountId: String,
            amount: String,
            newBalance: String,
            durationMs: Long
    ) {
        withdrawalCount.incrementAndGet()
        transactionsInWindow.incrementAndGet()
        withdrawalDurations.addAndGet(durationMs)

        logOperation(
                operation = "WITHDRAWAL",
                status = "SUCCESS",
                parameters = mapOf(
                        "transactionId" to transactionId,
                        "accountId" to accountId,
                        "amount" to amount,
                        "newBalance" to newBalance
                ),
                durationMs = durationMs
        )
        checkAndLogStats()
    }

    fun logDepositError(
            transactionId: String,
            accountId: String,
            amount: String,
            errorType: String,
            errorMessage: String
    ) {
        depositErrorCount.incrementAndGet()

        logOperation(
                operation = "DEPOSIT",
                status = "FAILURE",
                parameters = mapOf(
                        "transactionId" to transactionId,
                        "accountId" to accountId,
                        "amount" to amount
                ),
                reason = "$errorType: $errorMessage"
        )
    }

    fun logWithdrawalError(
            transactionId: String,
            accountId: String,
            amount: String,
            errorType: String,
            errorMessage: String
    ) {
        withdrawalErrorCount.incrementAndGet()

        logOperation(
                operation = "WITHDRAWAL",
                status = "FAILURE",
                parameters = mapOf(
                        "transactionId" to transactionId,
                        "accountId" to accountId,
                        "amount" to amount
                ),
                reason = "$errorType: $errorMessage"
        )
    }

    fun logInsufficientFunds(
            transactionId: String,
            accountId: String,
            requestedAmount: String,
            currentBalance: String
    ) {
        insufficientFundsCount.incrementAndGet()
        withdrawalErrorCount.incrementAndGet()

        logOperation(
                operation = "WITHDRAWAL",
                status = "FAILURE",
                parameters = mapOf(
                        "transactionId" to transactionId,
                        "accountId" to accountId,
                        "requestedAmount" to requestedAmount,
                        "currentBalance" to currentBalance
                ),
                reason = "Insufficient funds"
        )
    }

    fun logBalanceCheck(
            accountId: Long,
            isConsistent: Boolean,
            storedBalance: BigDecimal?,
            calculatedBalance: BigDecimal?
    ) {
        val status = if (isConsistent) "SUCCESS" else "FAILURE"
        val reason = if (!isConsistent) "Balance inconsistency: stored=$storedBalance, calculated=$calculatedBalance" else null

        logOperation(
                operation = "BALANCE_CHECK",
                status = status,
                parameters = mapOf(
                        "accountId" to accountId.toString(),
                        "storedBalance" to storedBalance?.toString(),
                        "calculatedBalance" to calculatedBalance?.toString()
                ),
                reason = reason
        )
    }

    private fun checkAndLogStats() {
        val totalTransactions = depositCount.get() + withdrawalCount.get()
        if (totalTransactions % 100 == 0L && totalTransactions > 0) {
            val errorRate = (depositErrorCount.get() + withdrawalErrorCount.get()).toDouble() / totalTransactions
            val avgDepositDuration = if (depositCount.get() > 0) depositDurations.get() / depositCount.get() else 0
            val avgWithdrawalDuration = if (withdrawalCount.get() > 0) withdrawalDurations.get() / withdrawalCount.get() else 0

            val logEntry = mapOf(
                    "event" to "TRANSACTION_STATS",
                    "timestamp" to LocalDateTime.now().format(timestampFormat),
                    "totalTransactions" to totalTransactions,
                    "deposits" to depositCount.get(),
                    "withdrawals" to withdrawalCount.get(),
                    "depositErrors" to depositErrorCount.get(),
                    "withdrawalErrors" to withdrawalErrorCount.get(),
                    "insufficientFunds" to insufficientFundsCount.get(),
                    "errorRate" to String.format("%.2f%%", errorRate * 100),
                    "avgDepositDurationMs" to avgDepositDuration,
                    "avgWithdrawalDurationMs" to avgWithdrawalDuration
            )

            monitorLogger.info(objectMapper.writeValueAsString(logEntry))
        }
    }

    @Scheduled(fixedRate = 60_000)
    fun logTPS() {
        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - windowStart.get()) / 1000.0
        val tps = if (elapsedSeconds > 0) transactionsInWindow.get() / elapsedSeconds else 0.0

        val logEntry = mapOf(
                "event" to "TPS_REPORT",
                "timestamp" to LocalDateTime.now().format(timestampFormat),
                "transactions" to transactionsInWindow.get(),
                "elapsedSeconds" to String.format("%.2f", elapsedSeconds),
                "tps" to String.format("%.2f", tps)
        )

        monitorLogger.info(objectMapper.writeValueAsString(logEntry))

        transactionsInWindow.set(0)
        windowStart.set(currentTime)
    }

    fun resetCounters() {
        val logEntry = mapOf(
                "event" to "COUNTER_RESET",
                "timestamp" to LocalDateTime.now().format(timestampFormat),
                "deposits" to depositCount.get(),
                "withdrawals" to withdrawalCount.get(),
                "depositErrors" to depositErrorCount.get(),
                "withdrawalErrors" to withdrawalErrorCount.get(),
                "insufficientFunds" to insufficientFundsCount.get()
        )

        monitorLogger.info(objectMapper.writeValueAsString(logEntry))

        depositCount.set(0)
        withdrawalCount.set(0)
        depositErrorCount.set(0)
        withdrawalErrorCount.set(0)
        insufficientFundsCount.set(0)
        depositDurations.set(0)
        withdrawalDurations.set(0)
    }
}