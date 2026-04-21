package io.gfonseca.ledger.domain.validation;

public interface LedgerRule {
    void validate(LedgerContext ctx);
}
