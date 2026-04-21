package io.gfonseca.ledger.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Transaction(
        String id,
        TransactionType type,
        String description,
        String idempotencyKey,
        String reversesTransactionId,
        List<Posting> postings,
        Map<String, Object> metadata,
        Instant createdAt) {
    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(postings, "postings");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(createdAt, "createdAt");
        postings = List.copyOf(postings);
    }
}
