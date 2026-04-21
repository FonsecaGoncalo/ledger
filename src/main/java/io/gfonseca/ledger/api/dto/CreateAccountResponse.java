package io.gfonseca.ledger.api.dto;

import java.time.Instant;
import java.util.Map;

public record CreateAccountResponse(
        String accountId,
        String externalRef,
        String type,
        String currency,
        Map<String, Object> metadata,
        Instant createdAt) {}
