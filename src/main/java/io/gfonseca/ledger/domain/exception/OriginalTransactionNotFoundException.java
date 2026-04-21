package io.gfonseca.ledger.domain.exception;

public final class OriginalTransactionNotFoundException extends LedgerException {

    private final String transactionId;

    public OriginalTransactionNotFoundException(String transactionId) {
        super("Original transaction not found: " + transactionId);
        this.transactionId = transactionId;
    }

    public String transactionId() {
        return transactionId;
    }
}
