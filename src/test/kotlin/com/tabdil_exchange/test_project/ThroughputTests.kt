package com.tabdil_exchange.test_project

import com.fasterxml.jackson.databind.ObjectMapper
import com.tabdil_exchange.test_project.features.account.model.Account
import com.tabdil_exchange.test_project.features.account.repository.AccountRepository
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionRequest
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionDepositResponse
import com.tabdil_exchange.test_project.features.transaction.model.dto.TransactionWithdrawalResponse
import com.tabdil_exchange.test_project.features.transaction.repository.TransactionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
class ThroughputTests {
    private val log = LoggerFactory.getLogger(ThroughputTests::class.java)

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
    fun `Measure TPS under concurrent load`() {
        transactionRepository.deleteAll()
        accountRepository.deleteAll()

        val accountIds = listOf(1003L, 1034L, 12345678L, 438738L)
        val initialBalance = 10000.0
        accountIds.forEach{
            val account = Account(accountId = it, balance = initialBalance)
            accountRepository.save(account)
        }


        val numThreads = 10
        val requestsPerThread = 5
        val depositAmount = 100.0
        val withdrawalAmount = 100.0
        val executor = Executors.newFixedThreadPool(numThreads)
        val transactionIdStarter = 300000000L
        val successCount = AtomicInteger(0)
        val totalRequests = AtomicInteger(0)
        val durationMs = measureTimeMillis {
            for (i in 1..numThreads) {
                executor.submit {
                    var transactionId = transactionIdStarter + i
                    for (j in 1..requestsPerThread) {
                        try {
                            val isDeposit = ((transactionId % 2) == 0L)
                            val amount = if (isDeposit) depositAmount else withdrawalAmount
                            val endpoint = if (isDeposit) "/api/transactions/deposit" else "/api/transactions/withdraw"
                            val transactionRequest = TransactionRequest(
                                    transaction_id = transactionId.toString(),
                                    account_id = accountIds.shuffled()[0].toString(),
                                    amount = amount.toString()
                            )
                            val transactionRequestJson = objectMapper.writeValueAsString(transactionRequest)
                            val result = mockMvc.perform(
                                    MockMvcRequestBuilders.post(endpoint)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(transactionRequestJson)
                            ).andReturn()
                            val responseJson = result.response.contentAsString
                            val status = try {
                                if (isDeposit) {
                                    val response = objectMapper.readValue(responseJson, TransactionDepositResponse::class.java)
                                    response.status
                                } else {
                                    val response = objectMapper.readValue(responseJson, TransactionWithdrawalResponse::class.java)
                                    response.status
                                }
                            } catch (e: Exception) {
                                "failed"
                            }
                            if (status == "completed") {
                                successCount.incrementAndGet()
                            }
                            totalRequests.incrementAndGet()
                            Thread.sleep(10)
                        } catch (e: Exception) {
                            log.error("Error in transaction $transactionId", e)
                        }
                        transactionId += numThreads
                    }
                }
            }

            executor.shutdown()
            runBlocking {
                while (!executor.isTerminated) {
                    delay(100)
                }
            }
        }

        // Calculate TPS
        val tps = successCount.get() / (durationMs / 1000.0)

        log.info("====== Throughput Test Summary ======")
        log.info("Test Duration: ${durationMs / 1000.0} seconds")
        log.info("Number of Threads: $numThreads")
        log.info("Requests per Thread: $requestsPerThread")
        log.info("Total Requests: ${totalRequests.get()}")
        log.info("Successful Transactions: ${successCount.get()}")
        log.info("TPS: ${String.format("%.2f", tps)} transactions/second")
        log.info("====================================")

        // Verify
        Assertions.assertTrue(successCount.get() > 0, "Some transactions should succeed")
        Assertions.assertTrue(tps > 0, "TPS should be positive")
    }
}