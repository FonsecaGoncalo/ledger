package io.gfonseca.ledger.domain.exception;

public final class AlreadyReversedException extends LedgerException {

    private final String originalTransactionId;

    public AlreadyReversedException(String originalTransactionId) {
        super("Transaction has already been reversed: " + originalTransactionId);
        this.originalTransactionId = originalTransactionId;
    }

    public String originalTransactionId() {
        return originalTransactionId;
    }
}
