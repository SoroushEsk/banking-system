package com.tabdil_exchange.test_project.util

import com.tabdil_exchange.test_project.features.account.repository.AccountRepository
import com.tabdil_exchange.test_project.features.transaction.model.TransactionType
import com.tabdil_exchange.test_project.features.transaction.repository.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal
import kotlin.system.measureTimeMillis

@Component
@EnableScheduling
class BalanceCheckScheduler(
        private val accountRepository: AccountRepository,
        private val transactionRepository: TransactionRepository,
        private val transactionLogger: TransactionLogger
) {
    private val logger = LoggerFactory.getLogger(BalanceCheckScheduler::class.java)
    private var balanceCheckRuns = 0
    private var totalInconsistentAccounts = 0
    private var totalAccountsChecked = 0

    @Scheduled(fixedRateString = "\${balance.check.interval:3600000}")
    fun checkAccountBalances() {
        val durationMs = measureTimeMillis {
            transactionLogger.logOperation(
                    operation = "BALANCE_VERIFICATION",
                    status = "START"
            )
            try {
                val accountIds = accountRepository.findAll()
                transactionLogger.logOperation(
                        operation = "FETCH_ALL_ACCOUNTS",
                        status = "SUCCESS",
                        parameters = mapOf("accountCount" to accountIds.size.toString())
                )
                var consistentCount = 0
                var inconsistentCount = 0

                accountIds.forEach { account ->
                    try {
                        val storedBalance = account.balance.toBigDecimal()
                        val calculatedBalance = calculateBalanceFromTransactions(account.accountId)
                        val isConsistent = storedBalance.compareTo(calculatedBalance) == 0

                        transactionLogger.logBalanceCheck(
                                accountId = account.accountId,
                                isConsistent = isConsistent,
                                storedBalance = storedBalance,
                                calculatedBalance = calculatedBalance
                        )

                        if (isConsistent) {
                            consistentCount++
                        } else {
                            inconsistentCount++
                            logger.warn(
                                    "Balance inconsistency detected for account ${account.accountId}: " +
                                            "stored=$storedBalance, calculated=$calculatedBalance, " +
                                            "transactionCount=${transactionRepository.findAllWithTiming().filter { it.accountId == account.accountId }.size}"
                            )
                        }
                    } catch (e: Exception) {
                        logger.error("Error checking balance for account ${account.accountId}", e)
                        transactionLogger.logOperation(
                                operation = "BALANCE_CHECK",
                                status = "FAILURE",
                                parameters = mapOf("accountId" to account.accountId.toString()),
                                reason = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    }
                }

                balanceCheckRuns++
                totalInconsistentAccounts += inconsistentCount
                totalAccountsChecked += accountIds.size
                val inconsistencyRate = if (totalAccountsChecked > 0) {
                    (totalInconsistentAccounts.toDouble() / totalAccountsChecked) * 100
                } else 0.0

                transactionLogger.logOperation(
                        operation = "BALANCE_VERIFICATION",
                        status = "SUCCESS",
                        parameters = mapOf(
                                "consistentCount" to consistentCount.toString(),
                                "inconsistentCount" to inconsistentCount.toString(),
                                "accountsProcessed" to accountIds.size.toString(),
                                "runNumber" to balanceCheckRuns.toString(),
                                "inconsistencyRate" to String.format("%.2f%%", inconsistencyRate)
                        ),
                        durationMs = -1
                )
                transactionLogger.resetCounters()
            } catch (e: Exception) {
                logger.error("Error during account balance verification", e)
                transactionLogger.logOperation(
                        operation = "BALANCE_VERIFICATION",
                        status = "FAILURE",
                        reason = "${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    private fun calculateBalanceFromTransactions(accountId: Long): BigDecimal {
        val durationMs = measureTimeMillis {
            val allTransactions = transactionRepository.findAllWithTiming()
            transactionLogger.logOperation(
                    operation = "FETCH_ALL_TRANSACTIONS",
                    status = "SUCCESS",
                    parameters = mapOf("resultSize" to allTransactions.size.toString()),
                    durationMs = -1
            )

            val deposits = allTransactions
                    .filter { it.accountId == accountId && it.type == TransactionType.DEPOSIT }
                    .map { it.amount.toBigDecimal() }
            val withdrawals = allTransactions
                    .filter { it.accountId == accountId && it.type == TransactionType.WITHDRAWAL }
                    .map { it.amount.toBigDecimal() }

            val depositSum = deposits.reduceOrNull { acc, amount -> acc + amount } ?: BigDecimal.ZERO
            val withdrawalSum = withdrawals.reduceOrNull { acc, amount -> acc + amount } ?: BigDecimal.ZERO
            val calculatedBalance = depositSum.subtract(withdrawalSum)

            if (deposits.isNotEmpty() || withdrawals.isNotEmpty()) {
                transactionLogger.logOperation(
                        operation = "CALCULATE_BALANCE",
                        status = "SUCCESS",
                        parameters = mapOf(
                                "accountId" to accountId.toString(),
                                "depositCount" to deposits.size.toString(),
                                "withdrawalCount" to withdrawals.size.toString()
                        )
                )
            }
            return calculatedBalance
        }
        return BigDecimal.ZERO
    }
}