package io.gfonseca.ledger.domain.validation.rule

import io.gfonseca.ledger.domain.exception.AlreadyReversedException
import io.gfonseca.ledger.domain.exception.InvalidReversalException
import io.gfonseca.ledger.domain.exception.OriginalTransactionNotFoundException
import io.gfonseca.ledger.domain.model.Money
import io.gfonseca.ledger.domain.model.Posting
import io.gfonseca.ledger.domain.model.PostingRequest
import io.gfonseca.ledger.domain.model.Transaction
import io.gfonseca.ledger.domain.model.TransactionType
import io.gfonseca.ledger.domain.validation.LedgerContext
import io.gfonseca.ledger.store.LedgerStore
import spock.lang.Specification

import java.time.Instant

class ReversalMirrorRuleSpec extends Specification {

    def store = Mock(LedgerStore)
    def rule = new ReversalMirrorRule(store)

    def "no-op when reversesTransactionId is null"() {
        when:
        rule.validate(ctxOf(null, []))

        then:
        noExceptionThrown()
        0 * store._
    }

    def "passes when reversal postings exactly mirror original"() {
        given:
        def original = txnOf("txn_orig", TransactionType.DEPOSIT, [
            posting("acct_A", new Money(10000, "EUR")),
            posting("acct_B", new Money(-10000, "EUR"))
        ])
        def reversalPostings = [
            new PostingRequest("acct_A", new Money(-10000, "EUR")),
            new PostingRequest("acct_B", new Money(10000, "EUR"))
        ]

        when:
        rule.validate(ctxOf("txn_orig", reversalPostings))

        then:
        1 * store.findTransactionById("txn_orig") >> Optional.of(original)
        1 * store.hasReversal("txn_orig") >> false
        noExceptionThrown()
    }

    def "throws when original not found"() {
        when:
        rule.validate(ctxOf("txn_missing", []))

        then:
        1 * store.findTransactionById("txn_missing") >> Optional.empty()
        OriginalTransactionNotFoundException ex = thrown()
        ex.transactionId() == "txn_missing"
    }

    def "rejects reversing a reversal"() {
        given:
        def original = txnOf("txn_rev", TransactionType.REVERSAL, [
            posting("acct_A", new Money(-10000, "EUR")),
            posting("acct_B", new Money(10000, "EUR"))
        ])

        when:
        rule.validate(ctxOf("txn_rev", []))

        then:
        1 * store.findTransactionById("txn_rev") >> Optional.of(original)
        InvalidReversalException ex = thrown()
        ex.message.contains("Cannot reverse a reversal")
    }

    def "rejects when postings do not mirror exactly"() {
        given:
        def original = txnOf("txn_orig", TransactionType.DEPOSIT, [
            posting("acct_A", new Money(10000, "EUR")),
            posting("acct_B", new Money(-10000, "EUR"))
        ])
        def reversalPostings = [
            new PostingRequest("acct_A", new Money(-5000, "EUR")),
            new PostingRequest("acct_B", new Money(5000, "EUR"))
        ]

        when:
        rule.validate(ctxOf("txn_orig", reversalPostings))

        then:
        1 * store.findTransactionById("txn_orig") >> Optional.of(original)
        InvalidReversalException ex = thrown()
        ex.message.contains("do not mirror")
    }

    def "throws AlreadyReversedException when original is already reversed"() {
        given:
        def original = txnOf("txn_orig", TransactionType.DEPOSIT, [
            posting("acct_A", new Money(10000, "EUR")),
            posting("acct_B", new Money(-10000, "EUR"))
        ])
        def reversalPostings = [
            new PostingRequest("acct_A", new Money(-10000, "EUR")),
            new PostingRequest("acct_B", new Money(10000, "EUR"))
        ]

        when:
        rule.validate(ctxOf("txn_orig", reversalPostings))

        then:
        1 * store.findTransactionById("txn_orig") >> Optional.of(original)
        1 * store.hasReversal("txn_orig") >> true
        AlreadyReversedException ex = thrown()
        ex.originalTransactionId() == "txn_orig"
    }

    private static LedgerContext ctxOf(String reversesTransactionId, List<PostingRequest> postings) {
        return new LedgerContext(
                "idem-key",
                TransactionType.REVERSAL,
                "desc",
                postings,
                reversesTransactionId,
                [:],
                null
                )
    }

    private static Posting posting(String accountId, Money money) {
        return new Posting("post_" + accountId, "txn_orig", accountId, money, 1L, Instant.now())
    }

    private static Transaction txnOf(String id, TransactionType type, List<Posting> postings) {
        return new Transaction(id, type, "desc", "idem-orig", null, postings, [:], Instant.now())
    }
}
