package io.gfonseca.ledger.domain.model;

public enum AccountType {
    ASSET,
    LIABILITY,
    REVENUE,
    EXPENSE;

    public boolean isDebitNormal() {
        return this == ASSET || this == EXPENSE;
    }
}
