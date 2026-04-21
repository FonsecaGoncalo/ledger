package io.gfonseca.ledger.domain.exception;

public final class InsufficientFundsException extends LedgerException {

    private final String accountId;
    private final long requested;
    private final long available;

    public InsufficientFundsException(String accountId, long requested, long available) {
        super("Account " + accountId + " has insufficient funds");
        this.accountId = accountId;
        this.requested = requested;
        this.available = available;
    }

    public String accountId() {
        return accountId;
    }

    public long requested() {
        return requested;
    }

    public long available() {
        return available;
    }
}
