package com.tabdil_exchange.test_project.features.account.repository

import com.tabdil_exchange.test_project.features.account.model.Account
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AccountRepository: JpaRepository<Account, Long> {
    @Query("SELECT a FROM Account a WHERE a.accountId = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByIdLocked(@Param("id") id: Long): Account?


}