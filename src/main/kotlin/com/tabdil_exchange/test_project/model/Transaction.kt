package com.tabdil_exchange.test_project.model

import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
        name = "transactions",
        indexes = [
            Index(name = "idx_transaction_id", columnList = "transaction_id", unique = true)
        ]
)
data class Transaction()
