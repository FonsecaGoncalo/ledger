package io.gfonseca.ledger.domain.model;

import java.time.Instant;

public record BalanceView(String accountId, Money balance, Instant asOf, long postingCount) {}
