package io.gfonseca.ledger.api.dto;

import java.time.Instant;

public record BalanceResponse(String accountId, long balance, String currency, Instant asOf, long postingCount) {}
