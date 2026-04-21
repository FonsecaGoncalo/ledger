package io.gfonseca.ledger.domain.model;

import java.util.Objects;

public record Money(long amount, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
        if (currency.length() != 3) throw new IllegalArgumentException("currency must be ISO 4217 (3 chars)");
    }

    public Money add(Money other) {
        if (!currency.equals(other.currency))
            throw new IllegalArgumentException("currency mismatch: " + currency + " vs " + other.currency);
        return new Money(Math.addExact(amount, other.amount), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amount), currency);
    }
}
