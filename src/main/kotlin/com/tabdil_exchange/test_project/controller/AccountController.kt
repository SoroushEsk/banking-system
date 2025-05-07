package com.tabdil_exchange.test_project.controller

import com.tabdil_exchange.test_project.model.Account
import com.tabdil_exchange.test_project.model.AccountDot
import com.tabdil_exchange.test_project.service.AccountService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/accounts")
class AccountController(private val service: AccountService) {

    @RequestMapping("/")
    @ResponseBody
    fun greeting(): String {
        return "Hello, World"
    }
    @PostMapping
    fun createAccount(@RequestBody request: AccountDot): ResponseEntity<Account> {

        val created = service.createAccount(request.balance)
        return ResponseEntity.ok(created)
    }

    @GetMapping("/{accountId}")
    fun getAccount(@PathVariable accountId: Long): ResponseEntity<Account> {
        val account = service.getAccount(accountId)
        return ResponseEntity.ok(account)
    }
}