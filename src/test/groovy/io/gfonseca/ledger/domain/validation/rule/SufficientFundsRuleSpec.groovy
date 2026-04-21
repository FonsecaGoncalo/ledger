package io.gfonseca.ledger.domain.validation.rule

import io.gfonseca.ledger.domain.exception.InsufficientFundsException
import io.gfonseca.ledger.domain.model.Account
import io.gfonseca.ledger.domain.model.AccountType
import io.gfonseca.ledger.domain.model.Accounts
import io.gfonseca.ledger.domain.model.Money
import io.gfonseca.ledger.domain.model.PostingRequest
import io.gfonseca.ledger.domain.model.TransactionType
import io.gfonseca.ledger.domain.validation.LedgerContext
import io.gfonseca.ledger.store.LedgerStore
import spock.lang.Specification

import java.time.Instant

class SufficientFundsRuleSpec extends Specification {

    def store = Mock(LedgerStore)
    def rule = new SufficientFundsRule(store)

    def "short-circuits without querying balances when no account would be debited"() {
        given:
        // Positive to ASSET (credit from external origin, credit-normal pair has negative to LIABILITY)
        // No ASSET is debited, so no balance lookup is needed.
        def accounts = accountsOf([
            account("acct_asset", AccountType.ASSET, "EUR"),
            account("acct_liab", AccountType.LIABILITY, "EUR")
        ])
        def postings = [
            new PostingRequest("acct_asset", new Money(10000, "EUR")),
            new PostingRequest("acct_liab", new Money(-10000, "EUR"))
        ]

        when:
        rule.validate(ctxOf(postings, accounts))

        then:
        noExceptionThrown()
        0 * store._
    }

    def "passes when every debited account has enough balance"() {
        given:
        def accounts = accountsOf([
            account("acct_asset", AccountType.ASSET, "EUR"),
            account("acct_liab", AccountType.LIABILITY, "EUR")
        ])
        def postings = [
            new PostingRequest("acct_asset", new Money(-5000, "EUR")),
            new PostingRequest("acct_liab", new Money(5000, "EUR"))
        ]

        when:
        rule.validate(ctxOf(postings, accounts))

        then:
        1 * store.getBalances({ it.toSet() == ["acct_asset", "acct_liab"].toSet() }) >> [
            "acct_asset": 10000L,
            "acct_liab": 5000L
        ]
        noExceptionThrown()
    }

    def "throws InsufficientFundsException when debit would overdraw asset"() {
        given:
        def accounts = accountsOf([
            account("acct_asset", AccountType.ASSET, "EUR"),
            account("acct_liab", AccountType.LIABILITY, "EUR")
        ])
        def postings = [
            new PostingRequest("acct_asset", new Money(-6000, "EUR")),
            new PostingRequest("acct_liab", new Money(6000, "EUR"))
        ]

        when:
        rule.validate(ctxOf(postings, accounts))

        then:
        1 * store.getBalances({ it.toSet() == ["acct_asset", "acct_liab"].toSet() }) >> [
            "acct_asset": 5000L,
            "acct_liab": 10000L
        ]
        InsufficientFundsException ex = thrown()
        ex.accountId() == "acct_asset"
        ex.requested() == 6000L
        ex.available() == 5000L
    }

    def "applies credit-normal sign inversion for LIABILITY accounts"() {
        given:
        // Reducing a LIABILITY (credit-normal) is a positive posting; delta = -positive = negative => debited.
        def accounts = accountsOf([
            account("acct_asset", AccountType.ASSET, "EUR"),
            account("acct_liab", AccountType.LIABILITY, "EUR")
        ])
        def postings = [
            new PostingRequest("acct_asset", new Money(-2000, "EUR")),
            new PostingRequest("acct_liab", new Money(2000, "EUR"))
        ]

        when:
        rule.validate(ctxOf(postings, accounts))

        then:
        1 * store.getBalances({ it.toSet() == ["acct_asset", "acct_liab"].toSet() }) >> [
            "acct_asset": 5000L,
            "acct_liab": 1000L
        ]
        InsufficientFundsException ex = thrown()
        ex.accountId() == "acct_liab"
    }

    private static LedgerContext ctxOf(List<PostingRequest> postings, Accounts accounts) {
        return new LedgerContext(
                "idem-key",
                TransactionType.WITHDRAWAL,
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
