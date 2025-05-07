package com.tabdil_exchange.test_project.features.transaction.model

import com.tabdil_exchange.test_project.features.account.model.Account
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
        name = "transactions",
        indexes = [
            Index(name = "idx_transaction_id", columnList = "transaction_id", unique = true)
        ]
)
data class Transaction(

        @Id
        @Column(name = "transaction_id", nullable = false, length = 9)
        val transactionId: Long,

        @Column(name = "account_id", nullable = false)
        val accountId: Long,

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "account_id", referencedColumnName = "account_id", insertable = false, updatable = false)
        val account: Account? = null,

        @Column(nullable = false)
        val amount: Double,

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        val type: TransactionType,

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        var status: TransactionStatus,

        @Column(name = "created_at", nullable = false)
        val createdAt: LocalDateTime = LocalDateTime.now(),


        @Column(name = "failure_reason", length = 255)
        var failureReason: String? = null
)
enum class TransactionType{
        DEPOSIT, WITHDRAWAL
}
enum class TransactionStatus {
        PENDING, COMPLETED, FAILED
}