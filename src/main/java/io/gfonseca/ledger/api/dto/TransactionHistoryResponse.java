package io.gfonseca.ledger.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TransactionHistoryResponse(
        String accountId, List<TransactionItem> items, String nextCursor, boolean hasMore) {
    public record TransactionItem(
            String transactionId,
            String type,
            String description,
            List<PostingDto> postings,
            Map<String, Object> metadata,
            Instant createdAt) {}
}
