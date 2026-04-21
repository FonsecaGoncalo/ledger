package io.gfonseca.ledger.domain.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Sequences {

    private final Map<String, Long> nextByAccount;

    public Sequences(Map<String, Long> seqNumbersByAccountId) {
        Objects.requireNonNull(seqNumbersByAccountId, "seqNumbersByAccountId");
        this.nextByAccount = new HashMap<>(seqNumbersByAccountId);
    }

    public long next(String accountId) {
        Long seq = nextByAccount.get(accountId);
        if (seq == null) {
            throw new IllegalArgumentException("No sequence tracked for account " + accountId);
        }
        nextByAccount.put(accountId, seq + 1);
        return seq;
    }
}
