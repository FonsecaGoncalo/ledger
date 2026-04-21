package io.gfonseca.ledger.domain.service;

import io.gfonseca.ledger.domain.exception.AccountNotFoundException;
import io.gfonseca.ledger.domain.exception.TransactionNotFoundException;
import io.gfonseca.ledger.domain.model.Accounts;
import io.gfonseca.ledger.domain.model.BalanceView;
import io.gfonseca.ledger.domain.model.PagedResult;
import io.gfonseca.ledger.domain.model.PostingRequest;
import io.gfonseca.ledger.domain.model.Sequences;
import io.gfonseca.ledger.domain.model.Transaction;
import io.gfonseca.ledger.domain.model.TransactionType;
import io.gfonseca.ledger.domain.validation.LedgerContext;
import io.gfonseca.ledger.domain.validation.LedgerRulesEngine;
import io.gfonseca.ledger.store.LedgerStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private final LedgerStore store;
    private final IdempotencyService idempotency;
    private final AccountsService accountsService;
    private final LedgerRulesEngine rulesEngine;

    public LedgerService(
            LedgerStore store,
            IdempotencyService idempotency,
            AccountsService accountsService,
            LedgerRulesEngine rulesEngine) {
        this.store = store;
        this.idempotency = idempotency;
        this.accountsService = accountsService;
        this.rulesEngine = rulesEngine;
    }

    @Transactional(readOnly = true)
    public BalanceView getBalance(String accountId) {
        accountsService.findAccount(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        return store.getBalanceView(accountId);
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(String transactionId) {
        return store.findTransactionById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    @Transactional(readOnly = true)
    public PagedResult<Transaction> getTransactionHistory(String accountId, String cursor, int limit) {
        accountsService.findAccount(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        return store.getTransactionHistory(accountId, cursor, limit);
    }

    @Transactional
    public Transaction submitTransaction(
            String idempotencyKey,
            TransactionType type,
            String description,
            List<PostingRequest> postingRequests,
            String reversesTransactionId,
            Map<String, Object> metadata) {
        Optional<Transaction> existing = idempotency.find(idempotencyKey);
        if (existing.isPresent()) {
            return idempotency.reconcile(
                    existing.get(), type, description, postingRequests, reversesTransactionId, metadata);
        }

        Accounts accounts = accountsService.loadAndLock(postingRequests.stream()
                .map(PostingRequest::accountId)
                .distinct()
                .toList());

        LedgerContext ctx = new LedgerContext(
                idempotencyKey, type, description, postingRequests, reversesTransactionId, metadata, accounts);
        rulesEngine.validate(ctx);

        Sequences sequences = nextSequences(accounts.ids());

        Transaction txn = TransactionFactory.create(
                idempotencyKey, type, description, postingRequests, reversesTransactionId, metadata, sequences);

        return store.insertTransaction(txn);
    }

    private Sequences nextSequences(Set<String> accountIds) {
        return new Sequences(accountIds.stream().collect(Collectors.toMap(Function.identity(), store::nextSequenceNo)));
    }
}
