package io.gfonseca.ledger.domain.exception;

public sealed class LedgerException extends RuntimeException
        permits AccountNotFoundException,
                DuplicateAccountException,
                InsufficientFundsException,
                CurrencyMismatchException,
                InvalidTransactionException,
                DuplicateTransactionException,
                InvalidReversalException,
                AlreadyReversedException,
                OriginalTransactionNotFoundException,
                TransactionNotFoundException {

    protected LedgerException(String message) {
        super(message);
    }
}
