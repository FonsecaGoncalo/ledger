package io.gfonseca.ledger.domain.exception;

public final class DuplicateAccountException extends LedgerException {

    private final String externalRef;

    public DuplicateAccountException(String externalRef) {
        super("Account with externalRef already exists: " + externalRef);
        this.externalRef = externalRef;
    }

    public String externalRef() {
        return externalRef;
    }
}
