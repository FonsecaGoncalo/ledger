package io.gfonseca.ledger.domain.model;

import java.util.List;

public record PagedResult<T>(List<T> items, String nextCursor, boolean hasMore) {
    public PagedResult {
        items = List.copyOf(items);
    }
}
