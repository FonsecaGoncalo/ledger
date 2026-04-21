package io.gfonseca.ledger

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.Shared
import spock.lang.Specification

@SpringBootTest
abstract class AbstractIntegrationSpec extends Specification {

    @Shared
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine").tap { start() }

    @Autowired
    JdbcTemplate jdbcTemplate

    @Autowired
    WebApplicationContext webApplicationContext

    MockMvc mockMvc

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl)
        registry.add("spring.datasource.username", postgres::getUsername)
        registry.add("spring.datasource.password", postgres::getPassword)
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "25")
    }

    def setup() {
        jdbcTemplate.execute("TRUNCATE TABLE postings, transactions, accounts CASCADE")
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }
}
