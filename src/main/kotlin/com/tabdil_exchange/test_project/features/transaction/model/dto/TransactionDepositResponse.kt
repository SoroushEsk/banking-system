package com.tabdil_exchange.test_project.features.transaction.model.dto

data class TransactionDepositResponse(
    val transaction_id: String,
    val account_id: String,
    val new_balance: String,
    val status: String
)