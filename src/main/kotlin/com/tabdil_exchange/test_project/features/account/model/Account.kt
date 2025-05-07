package com.tabdil_exchange.test_project.features.account.model

import jakarta.persistence.*
import org.hibernate.annotations.Check
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.OptimisticLocking
import java.math.BigDecimal

@Entity
@Table(name = "accounts", indexes = [
    Index(name = "idx_account_id", columnList = "account_id", unique = true)
])
@Check(constraints = "balance >= 0")
@OptimisticLocking
data class Account(
        @Id
        @Column(name = "account_id", length = 9)
        var accountId: Long,


        @Column(nullable = false)
        var balance: Double,

        @Version
        @Column(name = "version")
        val version: Long = 0
){
        fun deposit(amount: Double): Double {
                require(amount > 0) { "Deposit amount must be positive" }
                balance += amount
                return balance
        }

        fun withdraw(amount: Double): Double {
                require(amount > 0) { "Withdrawal amount must be positive" }
                require(balance >= amount) { "Insufficient funds" }
                balance -= amount
                return balance
        }
}
