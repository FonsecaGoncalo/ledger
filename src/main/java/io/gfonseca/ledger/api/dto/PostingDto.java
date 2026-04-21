package io.gfonseca.ledger.api.dto;

public record PostingDto(String postingId, String accountId, long amount, String currency, long sequenceNo) {}
