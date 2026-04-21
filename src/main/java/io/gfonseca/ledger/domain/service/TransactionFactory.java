package io.gfonseca.ledger.domain.service;

import io.gfonseca.ledger.domain.model.KsuidGenerator;
import io.gfonseca.ledger.domain.model.Posting;
import io.gfonseca.ledger.domain.model.PostingRequest;
import io.gfonseca.ledger.domain.model.Sequences;
import io.gfonseca.ledger.domain.model.Transaction;
import io.gfonseca.ledger.domain.model.TransactionType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TransactionFactory {

    private TransactionFactory() {}

    public static Transaction create(
            String idempotencyKey,
            TransactionType type,
            String description,
            List<PostingRequest> postingRequests,
            String reversesTransactionId,
            Map<String, Object> metadata,
            Sequences sequences) {
        String txnId = KsuidGenerator.generate(KsuidGenerator.TRANSACTION_PREFIX);
        Instant now = Instant.now();

        List<Posting> postings = postingRequests.stream()
                .map(pr -> new Posting(
                        KsuidGenerator.generate(KsuidGenerator.POSTING_PREFIX),
                        txnId,
                        pr.accountId(),
                        pr.money(),
                        sequences.next(pr.accountId()),
                        now))
                .toList();

        return new Transaction(
                txnId, type, description, idempotencyKey, reversesTransactionId, postings, metadata, now);
    }
}
