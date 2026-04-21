package io.gfonseca.ledger.domain.exception;

public final class InvalidTransactionException extends LedgerException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
