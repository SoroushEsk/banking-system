package com.tabdil_exchange.test_project.service

import com.tabdil_exchange.test_project.model.Account
import com.tabdil_exchange.test_project.repository.AccountRepository
import org.springframework.stereotype.Service

@Service
class AccountService(
        private val repository: AccountRepository
) {
    fun createAccount(balance: Long): Account {
        require(balance >= 0) { "Balance cannot be negative" }
        val accounts = Account(balance = balance)
        repository.save(accounts)
        return accounts
    }
    fun getAccount(accountId: Long): Account = repository.findById(accountId).orElseThrow {
        NoSuchElementException("Account not found")
    }

}