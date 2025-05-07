package com.tabdil_exchange.test_project.model

import jakarta.persistence.*
import org.hibernate.annotations.Check

@Entity
@Table(name = "accounts", indexes = [
    Index(name = "idx_account_id", columnList = "account_id", unique = true)
])
@Check(constraints = "balance >= 0")
data class Account(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "account_id", unique = true, nullable = false, length = 9)
        val accountId: Long = 0,
        @Column(nullable = false)
        val balance: Long
)
