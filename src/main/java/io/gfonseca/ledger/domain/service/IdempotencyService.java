package io.gfonseca.ledger.domain.service;

import io.gfonseca.ledger.domain.exception.DuplicateTransactionException;
import io.gfonseca.ledger.domain.model.PostingRequest;
import io.gfonseca.ledger.domain.model.Transaction;
import io.gfonseca.ledger.domain.model.TransactionType;
import io.gfonseca.ledger.store.LedgerStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyService {

    private final LedgerStore store;

    public IdempotencyService(LedgerStore store) {
        this.store = store;
    }

    public Optional<Transaction> find(String idempotencyKey) {
        return store.findByIdempotencyKey(idempotencyKey);
    }

    public Transaction reconcile(
            Transaction existing,
            TransactionType type,
            String description,
            List<PostingRequest> postingRequests,
            String reversesTransactionId,
            Map<String, Object> metadata) {
        if (!payloadMatches(existing, type, description, postingRequests, reversesTransactionId, metadata)) {
            throw new DuplicateTransactionException(existing.idempotencyKey());
        }
        return existing;
    }

    private static boolean payloadMatches(
            Transaction existing,
            TransactionType type,
            String description,
            List<PostingRequest> postingRequests,
            String reversesTransactionId,
            Map<String, Object> metadata) {
        List<PostingRequest> existingPostings = existing.postings().stream()
                .map(p -> new PostingRequest(p.accountId(), p.money()))
                .toList();
        return existing.type() == type
                && Objects.equals(existing.description(), description)
                && Objects.equals(existing.reversesTransactionId(), reversesTransactionId)
                && Objects.equals(existing.metadata(), metadata)
                && asMultiset(existingPostings).equals(asMultiset(postingRequests));
    }

    private static Map<PostingRequest, Long> asMultiset(List<PostingRequest> requests) {
        return requests.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
