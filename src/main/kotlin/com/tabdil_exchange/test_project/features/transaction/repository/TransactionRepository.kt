package com.tabdil_exchange.test_project.features.transaction.repository

import com.tabdil_exchange.test_project.features.transaction.model.Transaction
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionRepository: JpaRepository<Transaction, String> {
}