package io.gfonseca.ledger.domain.validation.rule;

import io.gfonseca.ledger.domain.exception.AlreadyReversedException;
import io.gfonseca.ledger.domain.exception.InvalidReversalException;
import io.gfonseca.ledger.domain.exception.OriginalTransactionNotFoundException;
import io.gfonseca.ledger.domain.model.Money;
import io.gfonseca.ledger.domain.model.PostingRequest;
import io.gfonseca.ledger.domain.model.Transaction;
import io.gfonseca.ledger.domain.model.TransactionType;
import io.gfonseca.ledger.domain.validation.LedgerContext;
import io.gfonseca.ledger.domain.validation.LedgerRule;
import io.gfonseca.ledger.store.LedgerStore;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class ReversalMirrorRule implements LedgerRule {

    private final LedgerStore store;

    public ReversalMirrorRule(LedgerStore store) {
        this.store = store;
    }

    @Override
    @WithSpan
    public void validate(LedgerContext ctx) {
        String reversesTransactionId = ctx.reversesTransactionId();
        if (reversesTransactionId == null) {
            return;
        }

        Transaction original = store.findTransactionById(reversesTransactionId)
                .orElseThrow(() -> new OriginalTransactionNotFoundException(reversesTransactionId));

        if (original.type() == TransactionType.REVERSAL) {
            throw new InvalidReversalException("Cannot reverse a reversal: " + original.id());
        }

        Map<PostingKey, Long> expected = toMultiset(original.postings().stream()
                .map(p -> new PostingRequest(p.accountId(), p.money().negate()))
                .toList());
        Map<PostingKey, Long> actual = toMultiset(ctx.postings());

        if (!expected.equals(actual)) {
            throw new InvalidReversalException(
                    "Reversal postings do not mirror original "
                            + original.id()
                            + "; expected exact negation of each original posting (same account, same currency, negated amount)");
        }

        if (store.hasReversal(reversesTransactionId)) {
            throw new AlreadyReversedException(reversesTransactionId);
        }
    }

    private static Map<PostingKey, Long> toMultiset(List<PostingRequest> postings) {
        return postings.stream()
                .collect(Collectors.groupingBy(
                        posting -> new PostingKey(posting.accountId(), posting.money()), Collectors.counting()));
    }

    private record PostingKey(String accountId, Money money) {}
}
