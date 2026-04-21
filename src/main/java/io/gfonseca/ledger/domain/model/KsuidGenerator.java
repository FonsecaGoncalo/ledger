package io.gfonseca.ledger.domain.model;

import com.github.f4b6a3.ksuid.KsuidCreator;

public final class KsuidGenerator {

    public static final String ACCOUNT_PREFIX = "acct";
    public static final String TRANSACTION_PREFIX = "txn";
    public static final String POSTING_PREFIX = "pst";

    private KsuidGenerator() {}

    public static String generate(String prefix) {
        return prefix + "_" + KsuidCreator.getKsuid().toString();
    }
}
