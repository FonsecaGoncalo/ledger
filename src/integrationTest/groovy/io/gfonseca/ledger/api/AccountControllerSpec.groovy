package io.gfonseca.ledger.api

import tools.jackson.databind.ObjectMapper
import io.gfonseca.ledger.AbstractIntegrationSpec
import io.gfonseca.ledger.domain.model.AccountType
import io.gfonseca.ledger.domain.service.AccountsService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

import static org.hamcrest.Matchers.hasSize
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AccountControllerSpec extends AbstractIntegrationSpec {

    @Autowired
    ObjectMapper objectMapper

    @Autowired
    AccountsService accountsService

    private String toJson(Map body) {
        objectMapper.writeValueAsString(body)
    }

    def "POST /accounts returns 201 with the created account body"() {
        given:
        def body = [
            externalRef: "ext-ac-1",
            type: "ASSET",
            currency: "EUR",
            ownerName: "Acme Corp",
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath('$.accountId').exists())
                .andExpect(jsonPath('$.externalRef').value("ext-ac-1"))
                .andExpect(jsonPath('$.type').value("ASSET"))
                .andExpect(jsonPath('$.currency').value("EUR"))
                .andExpect(jsonPath('$.createdAt').exists())
    }

    def "POST /accounts with duplicate externalRef returns 409 DUPLICATE_ACCOUNT"() {
        given:
        def body = [
            externalRef: "ext-dup",
            type: "ASSET",
            currency: "EUR",
            metadata: [:]
        ]
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isCreated())

        expect:
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath('$.error').value("DUPLICATE_ACCOUNT"))
                .andExpect(jsonPath('$.details.externalRef').value("ext-dup"))
    }

    def "GET /accounts/{id} returns 200 with the account body"() {
        given:
        def account = accountsService.createAccount("ext-ac-get", AccountType.ASSET, "EUR", [:])

        expect:
        mockMvc.perform(get("/api/v1/accounts/${account.id()}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.accountId').value(account.id()))
                .andExpect(jsonPath('$.externalRef').value("ext-ac-get"))
    }

    def "GET /accounts/{id} returns 404 ACCOUNT_NOT_FOUND for unknown id"() {
        expect:
        mockMvc.perform(get("/api/v1/accounts/acct_nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.error').value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath('$.details.accountId').value("acct_nope"))
    }

    def "GET /accounts/{id}/balance returns 200 with zero balance for a fresh account"() {
        given:
        def account = accountsService.createAccount("ext-bal", AccountType.ASSET, "EUR", [:])

        expect:
        mockMvc.perform(get("/api/v1/accounts/${account.id()}/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.accountId').value(account.id()))
                .andExpect(jsonPath('$.balance').value(0))
                .andExpect(jsonPath('$.currency').value("EUR"))
                .andExpect(jsonPath('$.postingCount').value(0))
                .andExpect(jsonPath('$.asOf').exists())
    }

    def "GET /accounts/{id}/balance returns 404 ACCOUNT_NOT_FOUND for unknown id"() {
        expect:
        mockMvc.perform(get("/api/v1/accounts/acct_missing/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.error').value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath('$.details.accountId').value("acct_missing"))
    }

    def "GET /accounts paginates results and surfaces hasMore/nextCursor"() {
        given:
        3.times { i ->
            accountsService.createAccount("ext-list-${i}", AccountType.ASSET, "EUR", [:])
        }

        when: "first page with limit=2"
        def firstResponse = mockMvc.perform(get("/api/v1/accounts")
                .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items', hasSize(2)))
                .andExpect(jsonPath('$.hasMore').value(true))
                .andExpect(jsonPath('$.nextCursor').exists())
                .andReturn()

        def cursor = objectMapper.readValue(firstResponse.response.contentAsString, Map).nextCursor

        then: "follow-up page drains the remaining account"
        mockMvc.perform(get("/api/v1/accounts")
                .param("limit", "2")
                .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items', hasSize(1)))
                .andExpect(jsonPath('$.hasMore').value(false))
    }

    def "GET /accounts clamps limit below 1 up to 1"() {
        given:
        2.times { i ->
            accountsService.createAccount("ext-clamp-lo-${i}", AccountType.ASSET, "EUR", [:])
        }

        expect:
        mockMvc.perform(get("/api/v1/accounts")
                .param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items', hasSize(1)))
                .andExpect(jsonPath('$.hasMore').value(true))
    }

    def "GET /accounts uses default limit when none is provided"() {
        given:
        3.times { i ->
            accountsService.createAccount("ext-default-${i}", AccountType.ASSET, "EUR", [:])
        }

        expect:
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items', hasSize(3)))
                .andExpect(jsonPath('$.hasMore').value(false))
    }

    def "POST /accounts returns 400 VALIDATION_ERROR when externalRef is blank"() {
        given:
        def body = [
            externalRef: "",
            type: "ASSET",
            currency: "EUR",
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.error').value("VALIDATION_ERROR"))
                .andExpect(jsonPath('$.details.externalRef').exists())
    }

    def "POST /accounts returns 400 VALIDATION_ERROR when type is missing"() {
        given:
        def body = [
            externalRef: "ext-v-1",
            currency: "EUR",
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.error').value("VALIDATION_ERROR"))
                .andExpect(jsonPath('$.details.type').exists())
    }

    def "POST /accounts returns 400 VALIDATION_ERROR when currency is missing"() {
        given:
        def body = [
            externalRef: "ext-v-2",
            type: "ASSET",
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.error').value("VALIDATION_ERROR"))
                .andExpect(jsonPath('$.details.currency').exists())
    }

    def "POST /accounts returns 400 VALIDATION_ERROR when currency is lowercase"() {
        given:
        def body = [
            externalRef: "ext-v-3",
            type: "ASSET",
            currency: "usd",
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.error').value("VALIDATION_ERROR"))
                .andExpect(jsonPath('$.details.currency').exists())
    }

    def "POST /accounts returns 400 VALIDATION_ERROR when ownerName exceeds 255 chars"() {
        given:
        def body = [
            externalRef: "ext-v-4",
            type: "ASSET",
            currency: "EUR",
            ownerName: "A" * 256,
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.error').value("VALIDATION_ERROR"))
                .andExpect(jsonPath('$.details.ownerName').exists())
    }
}
