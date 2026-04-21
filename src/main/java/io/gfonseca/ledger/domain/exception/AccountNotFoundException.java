package io.gfonseca.ledger.domain.exception;

public final class AccountNotFoundException extends LedgerException {

    private final String accountId;

    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId);
        this.accountId = accountId;
    }

    public String accountId() {
        return accountId;
    }
}
