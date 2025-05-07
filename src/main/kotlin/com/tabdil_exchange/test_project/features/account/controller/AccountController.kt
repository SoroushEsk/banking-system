package com.tabdil_exchange.test_project.features.account.controller

import com.tabdil_exchange.test_project.features.account.model.Account
import com.tabdil_exchange.test_project.features.account.model.dto.AccountRequest
import com.tabdil_exchange.test_project.features.account.model.dto.AccountResponse
import com.tabdil_exchange.test_project.features.account.service.AccountService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/api/accounts")
class AccountController(private val service: AccountService) {

    @GetMapping("/{accountId}")
    fun getAccount(@PathVariable accountId: Long): ResponseEntity<AccountResponse> {
        val account = service.getAccount(accountId)
        return ResponseEntity.ok(AccountResponse(account.accountId.toString(), account.balance.toString()))
    }
    @GetMapping
    fun getAllAccounts(): List<AccountResponse>{
        return service.getAllAccounts().map{AccountResponse(it.accountId.toString(), it.balance.toString())}
    }
}