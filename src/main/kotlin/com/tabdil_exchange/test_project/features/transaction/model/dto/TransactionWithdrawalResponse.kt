package com.tabdil_exchange.test_project.features.transaction.model.dto

data class TransactionWithdrawalResponse(
    val account_id: String,
    val current_balance: String,
    val requested_amount: String,
    val status: String,
    val transaction_id: String
)