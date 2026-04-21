package io.gfonseca.ledger.domain.exception;

public final class CurrencyMismatchException extends LedgerException {

    private final String accountId;
    private final String requestedCurrency;
    private final String accountCurrency;

    public CurrencyMismatchException(String accountId, String requestedCurrency, String accountCurrency) {
        super("Currency mismatch on account " + accountId + ": posting is " + requestedCurrency + " but account is "
                + accountCurrency);
        this.accountId = accountId;
        this.requestedCurrency = requestedCurrency;
        this.accountCurrency = accountCurrency;
    }

    public String accountId() {
        return accountId;
    }

    public String requestedCurrency() {
        return requestedCurrency;
    }

    public String accountCurrency() {
        return accountCurrency;
    }
}
