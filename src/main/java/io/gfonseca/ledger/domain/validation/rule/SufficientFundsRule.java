package io.gfonseca.ledger.domain.validation.rule;

import io.gfonseca.ledger.domain.exception.InsufficientFundsException;
import io.gfonseca.ledger.domain.model.Account;
import io.gfonseca.ledger.domain.model.Money;
import io.gfonseca.ledger.domain.model.PostingRequest;
import io.gfonseca.ledger.domain.validation.LedgerContext;
import io.gfonseca.ledger.domain.validation.LedgerRule;
import io.gfonseca.ledger.store.LedgerStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
public class SufficientFundsRule implements LedgerRule {

    private final LedgerStore store;

    public SufficientFundsRule(LedgerStore store) {
        this.store = store;
    }

    @Override
    public void validate(LedgerContext ctx) {
        Map<String, Money> deltaByAccount = new HashMap<>();

        for (PostingRequest posting : ctx.postings()) {
            Account account = ctx.accounts().get(posting.accountId());
            Money delta = account.type().isDebitNormal()
                    ? posting.money()
                    : posting.money().negate();
            deltaByAccount.merge(posting.accountId(), delta, Money::add);
        }

        List<String> debited = deltaByAccount.entrySet().stream()
                .filter(e -> e.getValue().amount() < 0)
                .map(Map.Entry::getKey)
                .toList();
        if (debited.isEmpty()) {
            return;
        }

        Map<String, Long> balances = store.getBalances(debited);
        for (String accountId : debited) {
            long delta = deltaByAccount.get(accountId).amount();
            long balance = balances.getOrDefault(accountId, 0L);
            if (balance + delta < 0) {
                throw new InsufficientFundsException(accountId, -delta, balance);
            }
        }
    }
}
