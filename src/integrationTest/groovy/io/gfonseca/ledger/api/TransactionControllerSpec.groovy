package io.gfonseca.ledger.api

import tools.jackson.databind.ObjectMapper
import io.gfonseca.ledger.AbstractIntegrationSpec
import io.gfonseca.ledger.domain.model.AccountType
import io.gfonseca.ledger.domain.model.Money
import io.gfonseca.ledger.domain.model.PostingRequest
import io.gfonseca.ledger.domain.model.TransactionType
import io.gfonseca.ledger.domain.service.AccountsService
import io.gfonseca.ledger.domain.service.LedgerService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

import static org.hamcrest.Matchers.greaterThanOrEqualTo
import static org.hamcrest.Matchers.hasSize
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class TransactionControllerSpec extends AbstractIntegrationSpec {

    @Autowired
    ObjectMapper objectMapper

    @Autowired
    LedgerService ledgerService

    @Autowired
    AccountsService accountsService

    private String toJson(Map body) {
        objectMapper.writeValueAsString(body)
    }

    private Map depositPayload(String idem, String assetId, String revenueId, long amount = 10000) {
        [
            idempotencyKey: idem,
            type: "DEPOSIT",
            description: "deposit",
            postings: [
                [accountId: assetId, amount: amount, currency: "EUR"],
                [accountId: revenueId, amount: -amount, currency: "EUR"]
            ],
            metadata: [:]
        ]
    }

    def "POST /transactions returns 201 with the created transaction body"() {
        given:
        def asset = accountsService.createAccount("asset-tc-1", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-tc-1", AccountType.REVENUE, "EUR", [:])

        when:
        def result = mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(depositPayload("idem-tc-1", asset.id(), revenue.id()))))

        then:
        result.andExpect(status().isCreated())
                .andExpect(jsonPath('$.transactionId').exists())
                .andExpect(jsonPath('$.type').value("DEPOSIT"))
                .andExpect(jsonPath('$.description').value("deposit"))
                .andExpect(jsonPath('$.idempotencyKey').value("idem-tc-1"))
                .andExpect(jsonPath('$.reversesTransactionId').doesNotExist())
                .andExpect(jsonPath('$.postings', hasSize(2)))
                .andExpect(jsonPath('$.postings[0].postingId').exists())
                .andExpect(jsonPath('$.postings[0].amount').exists())
                .andExpect(jsonPath('$.postings[0].currency').value("EUR"))
                .andExpect(jsonPath('$.postings[0].sequenceNo').exists())
                .andExpect(jsonPath('$.createdAt').exists())
    }

    def "GET /transactions/{id} returns 200 with the transaction"() {
        given:
        def asset = accountsService.createAccount("asset-tc-2", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-tc-2", AccountType.REVENUE, "EUR", [:])
        def txn = ledgerService.submitTransaction(
                "idem-tc-get", TransactionType.DEPOSIT, "get test",
                [
                    new PostingRequest(asset.id(), new Money(500, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-500, "EUR"))
                ],
                null, [:])

        expect:
        mockMvc.perform(get("/api/v1/transactions/${txn.id()}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.transactionId').value(txn.id()))
                .andExpect(jsonPath('$.idempotencyKey').value("idem-tc-get"))
                .andExpect(jsonPath('$.postings', hasSize(2)))
    }

    def "GET /transactions/{id} returns 404 TRANSACTION_NOT_FOUND for unknown id"() {
        expect:
        mockMvc.perform(get("/api/v1/transactions/txn_does_not_exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.error').value("TRANSACTION_NOT_FOUND"))
                .andExpect(jsonPath('$.details.transactionId').value("txn_does_not_exist"))
                .andExpect(jsonPath('$.timestamp').exists())
                .andExpect(jsonPath('$.traceId').exists())
    }

    def "GET /accounts/{id}/transactions returns newest-first history with pagination flags"() {
        given:
        def asset = accountsService.createAccount("asset-hist-1", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-hist-1", AccountType.REVENUE, "EUR", [:])
        3.times { i ->
            ledgerService.submitTransaction(
                    "idem-hist-${i}", TransactionType.DEPOSIT, "deposit ${i}",
                    [
                        new PostingRequest(asset.id(), new Money(100, "EUR")),
                        new PostingRequest(revenue.id(), new Money(-100, "EUR"))
                    ],
                    null, [:])
        }

        expect:
        mockMvc.perform(get("/api/v1/accounts/${asset.id()}/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.accountId').value(asset.id()))
                .andExpect(jsonPath('$.items', hasSize(3)))
                .andExpect(jsonPath('$.hasMore').value(false))
                .andExpect(jsonPath('$.items[0].description').value("deposit 2"))
                .andExpect(jsonPath('$.items[2].description').value("deposit 0"))
    }

    def "pagination returns nextCursor and the follow-up request drains the rest"() {
        given:
        def asset = accountsService.createAccount("asset-page-1", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-page-1", AccountType.REVENUE, "EUR", [:])
        3.times { i ->
            ledgerService.submitTransaction(
                    "idem-page-${i}", TransactionType.DEPOSIT, "page ${i}",
                    [
                        new PostingRequest(asset.id(), new Money(10, "EUR")),
                        new PostingRequest(revenue.id(), new Money(-10, "EUR"))
                    ],
                    null, [:])
        }

        when: "first page with limit=2"
        def firstResponse = mockMvc.perform(get("/api/v1/accounts/${asset.id()}/transactions")
                .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items', hasSize(2)))
                .andExpect(jsonPath('$.hasMore').value(true))
                .andExpect(jsonPath('$.nextCursor').exists())
                .andReturn()

        def firstBody = objectMapper.readValue(firstResponse.response.contentAsString, Map)
        def cursor = firstBody.nextCursor

        then: "follow-up page drains the remaining item"
        mockMvc.perform(get("/api/v1/accounts/${asset.id()}/transactions")
                .param("limit", "2")
                .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items', hasSize(1)))
                .andExpect(jsonPath('$.hasMore').value(false))
    }

    def "limit below 1 is clamped up to 1"() {
        given:
        def asset = accountsService.createAccount("asset-clamp-lo", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-clamp-lo", AccountType.REVENUE, "EUR", [:])
        2.times { i ->
            ledgerService.submitTransaction(
                    "idem-clamp-lo-${i}", TransactionType.DEPOSIT, "x",
                    [
                        new PostingRequest(asset.id(), new Money(1, "EUR")),
                        new PostingRequest(revenue.id(), new Money(-1, "EUR"))
                    ],
                    null, [:])
        }

        expect:
        mockMvc.perform(get("/api/v1/accounts/${asset.id()}/transactions")
                .param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items', hasSize(1)))
                .andExpect(jsonPath('$.hasMore').value(true))
    }

    def "limit above 200 does not truncate when real results are fewer"() {
        given:
        def asset = accountsService.createAccount("asset-clamp-hi", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-clamp-hi", AccountType.REVENUE, "EUR", [:])
        3.times { i ->
            ledgerService.submitTransaction(
                    "idem-clamp-hi-${i}", TransactionType.DEPOSIT, "x",
                    [
                        new PostingRequest(asset.id(), new Money(1, "EUR")),
                        new PostingRequest(revenue.id(), new Money(-1, "EUR"))
                    ],
                    null, [:])
        }

        expect:
        mockMvc.perform(get("/api/v1/accounts/${asset.id()}/transactions")
                .param("limit", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.items', hasSize(3)))
                .andExpect(jsonPath('$.hasMore').value(false))
    }

    def "idempotent replay with matching payload returns same transactionId"() {
        given:
        def asset = accountsService.createAccount("asset-idem-1", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-idem-1", AccountType.REVENUE, "EUR", [:])
        def body = toJson(depositPayload("idem-replay", asset.id(), revenue.id()))

        def firstResponse = mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn()
        def firstId = objectMapper.readValue(firstResponse.response.contentAsString, Map).transactionId

        when:
        def secondResponse = mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn()
        def secondId = objectMapper.readValue(secondResponse.response.contentAsString, Map).transactionId

        then:
        firstId == secondId
    }

    def "idempotency conflict on same key with different payload returns 409 DUPLICATE_TRANSACTION"() {
        given:
        def asset = accountsService.createAccount("asset-idem-conflict", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-idem-conflict", AccountType.REVENUE, "EUR", [:])

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(depositPayload("idem-conflict", asset.id(), revenue.id(), 500))))
                .andExpect(status().isCreated())

        expect: "same key, different amount"
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(depositPayload("idem-conflict", asset.id(), revenue.id(), 999))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath('$.error').value("DUPLICATE_TRANSACTION"))
                .andExpect(jsonPath('$.details.idempotencyKey').value("idem-conflict"))
    }

    def "POST /transactions returns 404 ACCOUNT_NOT_FOUND when a posting references an unknown account"() {
        given:
        def asset = accountsService.createAccount("asset-unknown", AccountType.ASSET, "EUR", [:])
        def body = [
            idempotencyKey: "idem-unknown",
            type: "DEPOSIT",
            description: "bad",
            postings: [
                [accountId: asset.id(), amount: 100, currency: "EUR"],
                [accountId: "acct_nope", amount: -100, currency: "EUR"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.error').value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath('$.details.accountId').value("acct_nope"))
    }

    def "POST /transactions returns 409 INSUFFICIENT_FUNDS when asset balance would go negative"() {
        given:
        def source = accountsService.createAccount("asset-src-insuf", AccountType.ASSET, "EUR", [:])
        def dest = accountsService.createAccount("asset-dst-insuf", AccountType.ASSET, "EUR", [:])
        def liability = accountsService.createAccount("liab-insuf", AccountType.LIABILITY, "EUR", [:])
        ledgerService.submitTransaction(
                "fund-insuf", TransactionType.DEPOSIT, "fund",
                [
                    new PostingRequest(source.id(), new Money(5000, "EUR")),
                    new PostingRequest(liability.id(), new Money(-5000, "EUR"))
                ],
                null, [:])

        def body = [
            idempotencyKey: "idem-overdraw",
            type: "TRANSFER",
            description: "overdraw",
            postings: [
                [accountId: source.id(), amount: -6000, currency: "EUR"],
                [accountId: dest.id(), amount: 6000, currency: "EUR"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath('$.error').value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath('$.details.accountId').value(source.id()))
                .andExpect(jsonPath('$.details.requested').value(6000))
                .andExpect(jsonPath('$.details.available').value(5000))
    }

    def "POST /transactions returns 422 CURRENCY_MISMATCH when posting currency differs from account currency"() {
        given:
        def eurAccount = accountsService.createAccount("asset-cur-1", AccountType.ASSET, "EUR", [:])
        def usdAccount = accountsService.createAccount("asset-cur-2", AccountType.ASSET, "USD", [:])

        def body = [
            idempotencyKey: "idem-cur",
            type: "TRANSFER",
            description: "bad currency",
            postings: [
                [accountId: eurAccount.id(), amount: 100, currency: "USD"],
                [accountId: usdAccount.id(), amount: -100, currency: "USD"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath('$.error').value("CURRENCY_MISMATCH"))
                .andExpect(jsonPath('$.details.accountId').value(eurAccount.id()))
                .andExpect(jsonPath('$.details.requestedCurrency').value("USD"))
                .andExpect(jsonPath('$.details.accountCurrency').value("EUR"))
    }

    def "POST /transactions returns 422 INVALID_TRANSACTION when postings do not sum to zero"() {
        given:
        def asset = accountsService.createAccount("asset-imbalance", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-imbalance", AccountType.REVENUE, "EUR", [:])

        def body = [
            idempotencyKey: "idem-imbalance",
            type: "DEPOSIT",
            description: "unbalanced",
            postings: [
                [accountId: asset.id(), amount: 100, currency: "EUR"],
                [accountId: revenue.id(), amount: -50, currency: "EUR"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath('$.error').value("INVALID_TRANSACTION"))
    }

    def "POST /transactions returns 422 INVALID_REVERSAL when REVERSAL has no reversesTransactionId"() {
        given:
        def asset = accountsService.createAccount("asset-rev-missing", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-rev-missing", AccountType.REVENUE, "EUR", [:])

        def body = [
            idempotencyKey: "idem-rev-missing",
            type: "REVERSAL",
            description: "rev without link",
            postings: [
                [accountId: asset.id(), amount: -100, currency: "EUR"],
                [accountId: revenue.id(), amount: 100, currency: "EUR"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath('$.error').value("INVALID_REVERSAL"))
    }

    def "POST /transactions returns 409 ALREADY_REVERSED for a second reversal of the same original"() {
        given:
        def asset = accountsService.createAccount("asset-rev-dup", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-rev-dup", AccountType.REVENUE, "EUR", [:])
        def original = ledgerService.submitTransaction(
                "idem-rev-dup-orig", TransactionType.DEPOSIT, "orig",
                [
                    new PostingRequest(asset.id(), new Money(10000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(-10000, "EUR"))
                ],
                null, [:])
        ledgerService.submitTransaction(
                "idem-rev-dup-first", TransactionType.REVERSAL, "first rev",
                [
                    new PostingRequest(asset.id(), new Money(-10000, "EUR")),
                    new PostingRequest(revenue.id(), new Money(10000, "EUR"))
                ],
                original.id(), [:])

        def body = [
            idempotencyKey: "idem-rev-dup-second",
            type: "REVERSAL",
            description: "second rev",
            reversesTransactionId: original.id(),
            postings: [
                [accountId: asset.id(), amount: -10000, currency: "EUR"],
                [accountId: revenue.id(), amount: 10000, currency: "EUR"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath('$.error').value("ALREADY_REVERSED"))
                .andExpect(jsonPath('$.details.originalTransactionId').value(original.id()))
    }

    def "POST /transactions returns 404 ORIGINAL_TRANSACTION_NOT_FOUND when reversesTransactionId does not exist"() {
        given:
        def asset = accountsService.createAccount("asset-rev-orig-missing", AccountType.ASSET, "EUR", [:])
        def revenue = accountsService.createAccount("rev-rev-orig-missing", AccountType.REVENUE, "EUR", [:])

        def body = [
            idempotencyKey: "idem-rev-orig-missing",
            type: "REVERSAL",
            description: "rev of ghost",
            reversesTransactionId: "txn_ghost",
            postings: [
                [accountId: asset.id(), amount: -100, currency: "EUR"],
                [accountId: revenue.id(), amount: 100, currency: "EUR"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.error').value("ORIGINAL_TRANSACTION_NOT_FOUND"))
                .andExpect(jsonPath('$.details.transactionId').value("txn_ghost"))
    }

    def "POST /transactions returns 400 VALIDATION_ERROR when idempotencyKey is blank"() {
        given:
        def body = [
            idempotencyKey: "",
            type: "DEPOSIT",
            description: "x",
            postings: [
                [accountId: "acct_a", amount: 1, currency: "EUR"],
                [accountId: "acct_b", amount: -1, currency: "EUR"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.error').value("VALIDATION_ERROR"))
                .andExpect(jsonPath('$.details.idempotencyKey').exists())
    }

    def "POST /transactions returns 400 VALIDATION_ERROR when type is null"() {
        given:
        def body = [
            idempotencyKey: "idem-v",
            description: "x",
            postings: [
                [accountId: "acct_a", amount: 1, currency: "EUR"],
                [accountId: "acct_b", amount: -1, currency: "EUR"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.error').value("VALIDATION_ERROR"))
                .andExpect(jsonPath('$.details.type').exists())
    }

    def "POST /transactions returns 400 VALIDATION_ERROR when postings is empty"() {
        given:
        def body = [
            idempotencyKey: "idem-v",
            type: "DEPOSIT",
            description: "x",
            postings: [],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.error').value("VALIDATION_ERROR"))
                .andExpect(jsonPath('$.details.postings').exists())
    }

    def "POST /transactions returns 400 VALIDATION_ERROR when a posting accountId is blank"() {
        given:
        def body = [
            idempotencyKey: "idem-v",
            type: "DEPOSIT",
            description: "x",
            postings: [
                [accountId: "", amount: 1, currency: "EUR"],
                [accountId: "acct_b", amount: -1, currency: "EUR"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.error').value("VALIDATION_ERROR"))
                .andExpect(jsonPath('$..details.*', hasSize(greaterThanOrEqualTo(1))))
    }

    def "POST /transactions returns 400 VALIDATION_ERROR when currency is lowercase"() {
        given:
        def body = [
            idempotencyKey: "idem-v",
            type: "DEPOSIT",
            description: "x",
            postings: [
                [accountId: "acct_a", amount: 1, currency: "eur"],
                [accountId: "acct_b", amount: -1, currency: "EUR"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.error').value("VALIDATION_ERROR"))
    }

    def "POST /transactions returns 400 VALIDATION_ERROR when currency length is wrong"() {
        given:
        def body = [
            idempotencyKey: "idem-v",
            type: "DEPOSIT",
            description: "x",
            postings: [
                [accountId: "acct_a", amount: 1, currency: "EURO"],
                [accountId: "acct_b", amount: -1, currency: "EUR"]
            ],
            metadata: [:]
        ]

        expect:
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.error').value("VALIDATION_ERROR"))
    }
}
