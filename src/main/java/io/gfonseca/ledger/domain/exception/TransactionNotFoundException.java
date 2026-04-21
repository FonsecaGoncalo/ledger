package io.gfonseca.ledger.domain.exception;

public final class TransactionNotFoundException extends LedgerException {

    private final String transactionId;

    public TransactionNotFoundException(String transactionId) {
        super("Transaction not found: " + transactionId);
        this.transactionId = transactionId;
    }

    public String transactionId() {
        return transactionId;
    }
}
