package io.gfonseca.ledger.domain.service;

import io.gfonseca.ledger.domain.exception.AccountNotFoundException;
import io.gfonseca.ledger.domain.model.Account;
import io.gfonseca.ledger.domain.model.AccountType;
import io.gfonseca.ledger.domain.model.Accounts;
import io.gfonseca.ledger.domain.model.KsuidGenerator;
import io.gfonseca.ledger.domain.model.PagedResult;
import io.gfonseca.ledger.store.LedgerStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountsService {

    private final LedgerStore store;

    public AccountsService(LedgerStore store) {
        this.store = store;
    }

    @Transactional
    public Account createAccount(String externalRef, AccountType type, String currency, Map<String, Object> metadata) {
        Account account = new Account(
                KsuidGenerator.generate(KsuidGenerator.ACCOUNT_PREFIX),
                externalRef,
                type,
                currency.toUpperCase(),
                metadata,
                Instant.now());
        return store.insertAccount(account);
    }

    @Transactional(readOnly = true)
    public Optional<Account> findAccount(String id) {
        return store.findAccountById(id);
    }

    @Transactional(readOnly = true)
    public PagedResult<Account> listAccounts(String cursor, int limit) {
        return store.listAccounts(cursor, limit);
    }

    public Accounts loadAndLock(List<String> accountIds) {
        Map<String, Account> accounts = store.findAndLockAccounts(accountIds).stream()
                .collect(Collectors.toMap(Account::id, Function.identity()));

        for (String id : accountIds) {
            if (!accounts.containsKey(id)) throw new AccountNotFoundException(id);
        }

        return new Accounts(accounts);
    }
}
