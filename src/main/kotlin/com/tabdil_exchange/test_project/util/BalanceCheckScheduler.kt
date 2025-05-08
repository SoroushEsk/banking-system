package com.tabdil_exchange.test_project.util


import com.tabdil_exchange.test_project.features.account.repository.AccountRepository
import com.tabdil_exchange.test_project.features.transaction.model.TransactionType
import com.tabdil_exchange.test_project.features.transaction.repository.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Scheduled task to periodically check account balance consistency
 */
@Component
@EnableScheduling
class BalanceCheckScheduler(
        private val accountRepository: AccountRepository,
        private val transactionRepository: TransactionRepository,
        private val transactionLogger: TransactionLogger
) {
    private val logger = LoggerFactory.getLogger(BalanceCheckScheduler::class.java)

    @Scheduled(fixedRateString = "\${balance.check.interval:3600000}")
    fun checkAccountBalances() {
        logger.info("Starting scheduled account balance verification")

        try {
            val accountIds = accountRepository.findAll()

            var consistentCount = 0
            var inconsistentCount = 0

            accountIds.forEach { account ->
                try {
                    val storedBalance = account.balance.toBigDecimal()
                    val calculatedBalance = calculateBalanceFromTransactions(account.accountId)

                    val isConsistent = storedBalance.compareTo(calculatedBalance) == 0

                    // Log the balance check result
                    transactionLogger.logBalanceCheck(account.accountId, isConsistent, storedBalance, calculatedBalance)

                    if (isConsistent) {
                        consistentCount++
                    } else {
                        inconsistentCount++
                        logger.warn("Balance inconsistency detected for account $account.accountId: " +
                                "stored=$storedBalance, calculated=$calculatedBalance")
                    }
                } catch (e: Exception) {
                    logger.error("Error checking balance for account $account.accountId", e)
                }
            }

            logger.info("Balance verification completed: $consistentCount consistent, $inconsistentCount inconsistent")
            transactionLogger.resetCounters()

        } catch (e: Exception) {
            logger.error("Error during account balance verification", e)
        }
    }

    /**
     * Calculate account balance from transaction history
     */
    private fun calculateBalanceFromTransactions(accountId: Long): BigDecimal {
        val allTransactions = transactionRepository.findAll()
        val deposits = allTransactions
                .filter { it.accountId == accountId && it.type == TransactionType.DEPOSIT}
                .map { it.amount.toBigDecimal() }
                .reduceOrNull { acc, amount -> acc + amount } ?: BigDecimal.ZERO

        val withdrawals = allTransactions
                .filter { it.accountId == accountId && it.type == TransactionType.WITHDRAWAL}
                .map { it.amount.toBigDecimal() }
                .reduceOrNull { acc, amount -> acc + amount } ?: BigDecimal.ZERO

        return deposits.subtract(withdrawals)
    }
}