package io.gfonseca.ledger.domain.validation.rule;

import io.gfonseca.ledger.domain.exception.CurrencyMismatchException;
import io.gfonseca.ledger.domain.model.Account;
import io.gfonseca.ledger.domain.model.PostingRequest;
import io.gfonseca.ledger.domain.validation.LedgerContext;
import io.gfonseca.ledger.domain.validation.LedgerRule;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class CurrencyMatchRule implements LedgerRule {

    @Override
    @WithSpan
    public void validate(LedgerContext ctx) {
        for (PostingRequest posting : ctx.postings()) {
            Account account = ctx.accounts().get(posting.accountId());
            if (!account.currency().equals(posting.money().currency())) {
                throw new CurrencyMismatchException(
                        posting.accountId(), posting.money().currency(), account.currency());
            }
        }
    }
}
