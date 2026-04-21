package io.gfonseca.ledger.domain.validation.rule;

import io.gfonseca.ledger.domain.exception.InvalidTransactionException;
import io.gfonseca.ledger.domain.model.Money;
import io.gfonseca.ledger.domain.model.PostingRequest;
import io.gfonseca.ledger.domain.validation.LedgerContext;
import io.gfonseca.ledger.domain.validation.LedgerRule;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class BalancedPostingsRule implements LedgerRule {

    @Override
    public void validate(LedgerContext ctx) {
        List<PostingRequest> postings = ctx.postings();

        if (postings == null || postings.size() < 2) {
            throw new InvalidTransactionException("A transaction must have at least 2 postings");
        }

        postings.stream()
                .map(PostingRequest::money)
                .collect(Collectors.toMap(Money::currency, Function.identity(), Money::add))
                .forEach((currency, sum) -> {
                    if (sum.amount() != 0) {
                        throw new InvalidTransactionException(
                                "Postings do not balance for currency " + currency + ": sum is " + sum);
                    }
                });
    }
}
