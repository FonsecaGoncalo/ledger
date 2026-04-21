package io.gfonseca.ledger.domain.validation.rule

import io.gfonseca.ledger.domain.exception.InvalidReversalException
import io.gfonseca.ledger.domain.model.TransactionType
import io.gfonseca.ledger.domain.validation.LedgerContext
import spock.lang.Specification

class ReversalTypeLinkRuleSpec extends Specification {

    def rule = new ReversalTypeLinkRule()

    def "passes for REVERSAL with a link"() {
        when:
        rule.validate(ctxOf(TransactionType.REVERSAL, "txn_orig"))

        then:
        noExceptionThrown()
    }

    def "passes for non-REVERSAL without a link"() {
        when:
        rule.validate(ctxOf(type, null))

        then:
        noExceptionThrown()

        where:
        type << [
            TransactionType.DEPOSIT,
            TransactionType.WITHDRAWAL,
            TransactionType.TRANSFER,
            TransactionType.FEE
        ]
    }

    def "rejects REVERSAL without a link"() {
        when:
        rule.validate(ctxOf(TransactionType.REVERSAL, null))

        then:
        InvalidReversalException ex = thrown()
        ex.message.contains("REVERSAL")
    }

    def "rejects non-REVERSAL with a link"() {
        when:
        rule.validate(ctxOf(type, "txn_orig"))

        then:
        InvalidReversalException ex = thrown()
        ex.message.contains("reversesTransactionId")

        where:
        type << [
            TransactionType.DEPOSIT,
            TransactionType.WITHDRAWAL,
            TransactionType.TRANSFER,
            TransactionType.FEE
        ]
    }

    private static LedgerContext ctxOf(TransactionType type, String reversesTransactionId) {
        return new LedgerContext(
                "idem-key",
                type,
                "desc",
                [],
                reversesTransactionId,
                [:],
                null
                )
    }
}
