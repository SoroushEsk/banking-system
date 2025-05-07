package com.tabdil_exchange.test_project

import com.tabdil_exchange.test_project.features.account.model.Account
import com.tabdil_exchange.test_project.features.account.model.dto.AccountRequest
import com.tabdil_exchange.test_project.features.account.repository.AccountRepository
import com.tabdil_exchange.test_project.features.account.service.AccountService
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@ExtendWith(SpringExtension::class)
class TestProjectApplicationTests {
	@Autowired
	lateinit var accountService: AccountService

	@Autowired
	lateinit var accountRepository: AccountRepository

	companion object {
		@Container
		val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
			withDatabaseName("banking")
			withUsername("soroush")
			withPassword("Soroush1381")
		}

		@JvmStatic
		@DynamicPropertySource
		fun overrideProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.datasource.url", postgres::getJdbcUrl)
			registry.add("spring.datasource.username", postgres::getUsername)
			registry.add("spring.datasource.password", postgres::getPassword)
		}
	}
	@Test
	fun `should create account with valid balance`() {
		val account = accountService.createAccount(AccountRequest(
				balance = 3000.0,
				account_id = "123"
		))
		Assertions.assertNotNull(account.accountId)
		Assertions.assertEquals(3000, account.balance)
	}

	@Test
	fun `should throw exception when balance is negative`() {
		val exception = assertThrows<IllegalArgumentException> {
			accountService.createAccount(AccountRequest(
					balance = -3000.0,
					account_id = "1234"
			))
		}
		Assertions.assertEquals("Balance cannot be negative", exception.message)
	}

	@Test
	fun `db should enforce constraint when bypassing service validation`() {
		val account = Account(
				balance = -3000.0,
				accountId = 12345
		)
		assertThrows<Exception> {
			accountRepository.saveAndFlush(account)
		}
	}

}
