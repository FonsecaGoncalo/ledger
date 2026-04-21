package io.gfonseca.ledger.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record Account(
        String id,
        String externalRef,
        AccountType type,
        String currency,
        Map<String, Object> metadata,
        Instant createdAt) {
    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(externalRef, "externalRef");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
