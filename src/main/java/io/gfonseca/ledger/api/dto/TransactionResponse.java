package io.gfonseca.ledger.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TransactionResponse(
        String transactionId,
        String type,
        String description,
        String idempotencyKey,
        String reversesTransactionId,
        List<PostingDto> postings,
        Map<String, Object> metadata,
        Instant createdAt) {}
