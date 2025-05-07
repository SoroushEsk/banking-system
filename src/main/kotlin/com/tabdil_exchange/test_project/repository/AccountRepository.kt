package com.tabdil_exchange.test_project.repository

import com.tabdil_exchange.test_project.model.Account
import org.springframework.data.jpa.repository.JpaRepository

interface AccountRepository: JpaRepository<Account, Long> {
}