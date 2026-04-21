package io.gfonseca.ledger.domain.validation.rule

import io.gfonseca.ledger.domain.exception.CurrencyMismatchException
import io.gfonseca.ledger.domain.model.Account
import io.gfonseca.ledger.domain.model.AccountType
import io.gfonseca.ledger.domain.model.Accounts
import io.gfonseca.ledger.domain.model.Money
import io.gfonseca.ledger.domain.model.PostingRequest
import io.gfonseca.ledger.domain.model.TransactionType
import io.gfonseca.ledger.domain.validation.LedgerContext
import spock.lang.Specification

import java.time.Instant

class CurrencyMatchRuleSpec extends Specification {

    def rule = new CurrencyMatchRule()

    def "passes when every posting currency matches its account"() {
        given:
        def accounts = accountsOf([
            account("acct_A", AccountType.ASSET, "EUR"),
            account("acct_B", AccountType.LIABILITY, "EUR")
        ])
        def postings = [
            new PostingRequest("acct_A", new Money(10000, "EUR")),
            new PostingRequest("acct_B", new Money(-10000, "EUR"))
        ]

        when:
        rule.validate(ctxOf(postings, accounts))

        then:
        noExceptionThrown()
    }

    def "rejects when a posting's currency does not match the account's"() {
        given:
        def accounts = accountsOf([
            account("acct_A", AccountType.ASSET, "EUR"),
            account("acct_B", AccountType.LIABILITY, "EUR")
        ])
        def postings = [
            new PostingRequest("acct_A", new Money(10000, "USD")),
            new PostingRequest("acct_B", new Money(-10000, "EUR"))
        ]

        when:
        rule.validate(ctxOf(postings, accounts))

        then:
        CurrencyMismatchException ex = thrown()
        ex.accountId() == "acct_A"
        ex.requestedCurrency() == "USD"
        ex.accountCurrency() == "EUR"
    }

    private static LedgerContext ctxOf(List<PostingRequest> postings, Accounts accounts) {
        return new LedgerContext(
                "idem-key",
                TransactionType.DEPOSIT,
                "desc",
                postings,
                null,
                [:],
                accounts
                )
    }

    private static Account account(String id, AccountType type, String currency) {
        return new Account(id, "ext-" + id, type, currency, [:], Instant.now())
    }

    private static Accounts accountsOf(List<Account> list) {
        Map<String, Account> byId = [:]
        for (Account a : list) byId.put(a.id(), a)
        return new Accounts(byId)
    }
}
