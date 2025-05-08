package com.tabdil_exchange.test_project

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import com.fasterxml.jackson.databind.ObjectMapper
import com.tabdil_exchange.test_project.features.account.model.Account
import com.tabdil_exchange.test_project.features.account.repository.AccountRepository
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionRequest
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionWithdrawalResponse
import com.tabdil_exchange.test_project.features.transaction.repository.TransactionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.slf4j.LoggerFactory

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@ExtendWith(SpringExtension::class)
class ApplicationConcurrencyTests {
    private val log = LoggerFactory.getLogger(ApplicationConcurrencyTests::class.java)

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
        }
    }

    @Test
    fun `Concurrent withdrawals for double spending`() {
        transactionRepository.deleteAll()
        accountRepository.deleteAll()

        val numThreads = 2
        val initialBalance = 100.0
        val withdrawalAmount = initialBalance/numThreads
        val expectedBalanceAtTheEnd = initialBalance - (numThreads * withdrawalAmount)
        val accountId = 999999L
        val account = Account(accountId = accountId, balance = initialBalance)
        accountRepository.save(account)

        val executor = Executors.newFixedThreadPool(numThreads)
        val totalCount = AtomicInteger(numThreads)
        val successCount = AtomicInteger(numThreads)


        val transactionIdStarter = 200000000L
        for (i in 1..numThreads) {
            executor.submit {
                try {
                    val transactionWithdrawalRequest = TransactionRequest(
                            transaction_id = (transactionIdStarter + i).toString(),
                            account_id = accountId.toString(),
                            amount = withdrawalAmount.toString()
                    )
                    val transactionRequestJson = objectMapper.writeValueAsString(transactionWithdrawalRequest)
                    val requestResult = mockMvc.perform(
                            MockMvcRequestBuilders.post("http://localhost:8080/api/transactions/withdraw")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(transactionRequestJson)
                    ).andReturn()
                    log.info("===========================================================================")
                    log.info(requestResult.response.contentAsString)
                    log.info("===========================================================================")
//                    val transactionResponse = objectMapper.readValue(transactionResponseJson, TransactionWithdrawalResponse::class.java)
//                    val transactionResponseJson = requestResult.response.contentAsString


                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    totalCount.decrementAndGet()
                }
            }
        }

        runBlocking {
            val timeoutMillis = 60_000L
            val checkIntervalMillis = 1000L

            val startTime = System.currentTimeMillis()
            while (totalCount.get() != 0) {
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    println("Timeout: Not all requests completed in 1 minute")
                    break
                }
                delay(checkIntervalMillis)
            }

            executor.shutdown()
            delay(1000)
            val finalAccount = accountRepository.findById(accountId).orElseThrow()
            log.info("====== Final Test Summary ======")
            log.info("Expected Balance: $expectedBalanceAtTheEnd")
            log.info("NumberThreads: $numThreads")
            log.info("Success: ${successCount.get()}")
            log.info("Balance: ${finalAccount.balance}")
            log.info("===================================")
            Assertions.assertEquals(expectedBalanceAtTheEnd, finalAccount.balance, 0.001)
        }
    }

}