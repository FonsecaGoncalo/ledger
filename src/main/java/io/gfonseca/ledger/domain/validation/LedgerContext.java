package io.gfonseca.ledger.domain.validation;

import io.gfonseca.ledger.domain.model.Accounts;
import io.gfonseca.ledger.domain.model.PostingRequest;
import io.gfonseca.ledger.domain.model.TransactionType;
import java.util.List;
import java.util.Map;

public record LedgerContext(
        String idempotencyKey,
        TransactionType type,
        String description,
        List<PostingRequest> postings,
        String reversesTransactionId,
        Map<String, Object> metadata,
        Accounts accounts) {}
