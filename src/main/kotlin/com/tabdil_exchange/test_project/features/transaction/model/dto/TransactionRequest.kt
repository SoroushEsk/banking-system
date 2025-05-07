package com.tabdil_exchange.test_project.features.transaction.model.dto

data class TransactionRequest(
    val account_id: String,
    val amount: String,
    val transaction_id: String
)