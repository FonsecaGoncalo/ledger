package io.gfonseca.ledger.domain.validation.rule

import io.gfonseca.ledger.domain.exception.InvalidTransactionException
import io.gfonseca.ledger.domain.model.Money
import io.gfonseca.ledger.domain.model.PostingRequest
import io.gfonseca.ledger.domain.model.TransactionType
import io.gfonseca.ledger.domain.validation.LedgerContext
import spock.lang.Specification

class BalancedPostingsRuleSpec extends Specification {

    def rule = new BalancedPostingsRule()

    def "valid two-leg EUR transaction passes"() {
        given:
        def ctx = ctxOf([
            new PostingRequest("acct_A", new Money(10000, "EUR")),
            new PostingRequest("acct_B", new Money(-10000, "EUR"))
        ])

        when:
        rule.validate(ctx)

        then:
        noExceptionThrown()
    }

    def "valid multi-leg transaction passes when sum is zero"() {
        given:
        def ctx = ctxOf([
            new PostingRequest("acct_A", new Money(10000, "EUR")),
            new PostingRequest("acct_B", new Money(-7000, "EUR")),
            new PostingRequest("acct_C", new Money(-3000, "EUR"))
        ])

        when:
        rule.validate(ctx)

        then:
        noExceptionThrown()
    }

    def "rejects fewer than two postings"() {
        when:
        rule.validate(ctxOf(postings))

        then:
        thrown(InvalidTransactionException)

        where:
        postings << [
            null,
            [],
            [
                new PostingRequest("acct_A", new Money(10000, "EUR"))
            ]
        ]
    }

    def "rejects unbalanced single-currency postings"() {
        given:
        def ctx = ctxOf([
            new PostingRequest("acct_A", new Money(10000, "EUR")),
            new PostingRequest("acct_B", new Money(-9999, "EUR"))
        ])

        when:
        rule.validate(ctx)

        then:
        InvalidTransactionException ex = thrown()
        ex.message.contains("EUR")
    }

    def "validates each currency independently"() {
        given:
        def ctx = ctxOf([
            new PostingRequest("acct_A", new Money(10000, "EUR")),
            new PostingRequest("acct_B", new Money(-10000, "EUR")),
            new PostingRequest("acct_C", new Money(5000, "USD")),
            new PostingRequest("acct_D", new Money(-5000, "USD"))
        ])

        when:
        rule.validate(ctx)

        then:
        noExceptionThrown()
    }

    def "rejects unbalanced multi-currency when one currency is off"() {
        given:
        def ctx = ctxOf([
            new PostingRequest("acct_A", new Money(10000, "EUR")),
            new PostingRequest("acct_B", new Money(-10000, "EUR")),
            new PostingRequest("acct_C", new Money(5000, "USD")),
            new PostingRequest("acct_D", new Money(-4000, "USD"))
        ])

        when:
        rule.validate(ctx)

        then:
        InvalidTransactionException ex = thrown()
        ex.message.contains("USD")
    }

    def "multiple postings to same account sum correctly before balance check"() {
        given:
        def ctx = ctxOf([
            new PostingRequest("acct_A", new Money(6000, "EUR")),
            new PostingRequest("acct_A", new Money(4000, "EUR")),
            new PostingRequest("acct_B", new Money(-10000, "EUR"))
        ])

        when:
        rule.validate(ctx)

        then:
        noExceptionThrown()
    }

    private static LedgerContext ctxOf(List<PostingRequest> postings) {
        return new LedgerContext(
                "idem-key",
                TransactionType.DEPOSIT,
                "desc",
                postings,
                null,
                [:],
                null
                )
    }
}
