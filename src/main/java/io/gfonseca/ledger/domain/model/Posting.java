package io.gfonseca.ledger.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Posting(
        String id, String transactionId, String accountId, Money money, long sequenceNo, Instant createdAt) {
    public Posting {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(money, "money");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
