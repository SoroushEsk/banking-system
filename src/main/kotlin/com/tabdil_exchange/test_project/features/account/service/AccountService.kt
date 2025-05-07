package com.tabdil_exchange.test_project.features.account.service

import com.tabdil_exchange.test_project.features.account.model.Account
import com.tabdil_exchange.test_project.features.account.model.dto.AccountRequest
import com.tabdil_exchange.test_project.features.account.repository.AccountRepository
import org.springframework.stereotype.Service

@Service
class AccountService(
        private val repository: AccountRepository
) {
    fun createAccount(request: AccountRequest): Account {
        require(request.balance >= 0) { "Balance cannot be negative" }
        val accounts = Account(accountId = request.account_id.toLongOrNull() ?: throw NumberFormatException("Wrong account id format ${request.account_id}"), balance = request.balance)
        repository.save(accounts)
        return accounts
    }
    fun getAccount(accountId: Long): Account = repository.findById(accountId).orElseThrow {
        NoSuchElementException("Account not found")
    }

    fun getAllAccounts(): List<Account> = repository.findAll()

}