package com.tabdil_exchange.test_project

import com.fasterxml.jackson.databind.ObjectMapper
import com.tabdil_exchange.test_project.features.account.model.Account
import com.tabdil_exchange.test_project.features.account.repository.AccountRepository
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionRequest
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionWithdrawalResponse
import com.tabdil_exchange.test_project.features.transaction.repository.TransactionRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@ExtendWith(SpringExtension::class)
class NegativeBalanceTests {
    private val log = LoggerFactory.getLogger(NegativeBalanceTests::class.java)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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
            registry.add("spring.jpa.hibernate.ddl-auto", { "update" })
            registry.add("spring.jpa.show-sql", { "true" })
        }
    }

    @Test
    fun `Single withdrawal exceeding balance fails`() {
        transactionRepository.deleteAll()
        accountRepository.deleteAll()

        val accountId = 1001L
        val initialBalance = 500.0
        val withdrawalAmount = 600.0
        val account = Account(accountId = accountId, balance = initialBalance)
        accountRepository.save(account)

        val transactionRequest = TransactionRequest(
                transaction_id = "123456789",
                account_id = accountId.toString(),
                amount = withdrawalAmount.toString()
        )
        val transactionRequestJson = objectMapper.writeValueAsString(transactionRequest)

        val durationMs = measureTimeMillis {
            val result = mockMvc.perform(
                    MockMvcRequestBuilders.post("/api/transactions/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionRequestJson)
            ).andReturn()

            val responseJson = result.response.contentAsString
            val response = objectMapper.readValue(responseJson, TransactionWithdrawalResponse::class.java)
            Assertions.assertEquals("failed", response.status, "Withdrawal should fail due to insufficient funds")
            Assertions.assertEquals(initialBalance.toString(), response.current_balance, "Balance should remain unchanged")
            Assertions.assertEquals(withdrawalAmount.toString(), response.requested_amount, "Requested amount should match")

            val finalAccount = accountRepository.findById(accountId).orElseThrow()
            Assertions.assertEquals(initialBalance, finalAccount.balance, 0.001, "Account balance should not change")
        }

        log.info("Single withdrawal test completed in {} ms", durationMs)
    }


}