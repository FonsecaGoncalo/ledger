package io.gfonseca.ledger.domain.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record Accounts(Map<String, Account> byId) {

    public Accounts {
        Objects.requireNonNull(byId, "byId");
        byId = Map.copyOf(byId);
    }

    public Account get(String accountId) {
        return byId.get(accountId);
    }

    public Set<String> ids() {
        return byId.keySet();
    }
}
