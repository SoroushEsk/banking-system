package com.tabdil_exchange.test_project.features.transaction.repository

import com.tabdil_exchange.test_project.features.transaction.model.Transaction
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface TransactionRepository : JpaRepository<Transaction, String>, TransactionRepositoryCustom {
    @Query("SELECT t FROM Transaction t")
    override fun findAllWithTiming(): List<Transaction>
}

interface TransactionRepositoryCustom {
    fun findAllWithTiming(): List<Transaction>
}

class TransactionRepositoryImpl : TransactionRepositoryCustom {
    private val logger = LoggerFactory.getLogger(TransactionRepositoryImpl::class.java)

    override fun findAllWithTiming(): List<Transaction> {
        val start = System.currentTimeMillis()
        val result = (this as TransactionRepository).findAllWithTiming()
        val durationMs = System.currentTimeMillis() - start
        logger.info(
                "[DB_QUERY] operation=findAllWithTiming, durationMs=$durationMs, resultSize=${result.size}, correlationId=repository-$start"
        )
        return result
    }
}