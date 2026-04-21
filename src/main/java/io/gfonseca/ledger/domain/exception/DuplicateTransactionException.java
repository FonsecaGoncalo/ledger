package io.gfonseca.ledger.domain.exception;

public final class DuplicateTransactionException extends LedgerException {

    private final String idempotencyKey;

    public DuplicateTransactionException(String idempotencyKey) {
        super("Duplicate transaction with different payload for idempotency key: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
