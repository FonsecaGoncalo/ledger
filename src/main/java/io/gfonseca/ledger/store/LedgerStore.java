package io.gfonseca.ledger.store;

import io.gfonseca.ledger.domain.model.Account;
import io.gfonseca.ledger.domain.model.BalanceView;
import io.gfonseca.ledger.domain.model.PagedResult;
import io.gfonseca.ledger.domain.model.Transaction;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LedgerStore {

    Account insertAccount(Account account);

    Optional<Account> findAccountById(String id);

    Optional<Account> findAccountByExternalRef(String externalRef);

    PagedResult<Account> listAccounts(String cursor, int limit);

    // SELECT ... FOR UPDATE in sorted order to prevent deadlocks
    List<Account> findAndLockAccounts(List<String> accountIds);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findTransactionById(String transactionId);

    boolean hasReversal(String originalTransactionId);

    Transaction insertTransaction(Transaction transaction);

    // User-visible balances (debit-normal sign convention applied), keyed by accountId.
    // AccountsService not found are omitted from the result.
    Map<String, Long> getBalances(List<String> accountIds);

    // Next available sequence number for the account (max + 1)
    long nextSequenceNo(String accountId);

    BalanceView getBalanceView(String accountId);

    PagedResult<Transaction> getTransactionHistory(String accountId, String cursor, int limit);
}
