package io.gfonseca.ledger.api.dto;

import java.util.List;

public record ListAccountsResponse(List<CreateAccountResponse> items, String nextCursor, boolean hasMore) {}
