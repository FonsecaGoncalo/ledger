package io.gfonseca.ledger.domain.model;

import java.util.Objects;

public record PostingRequest(String accountId, Money money) {
    public PostingRequest {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(money, "money");
    }
}
