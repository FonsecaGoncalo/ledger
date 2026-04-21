package io.gfonseca.ledger.domain.validation.rule;

import io.gfonseca.ledger.domain.exception.InvalidReversalException;
import io.gfonseca.ledger.domain.model.TransactionType;
import io.gfonseca.ledger.domain.validation.LedgerContext;
import io.gfonseca.ledger.domain.validation.LedgerRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class ReversalTypeLinkRule implements LedgerRule {

    @Override
    public void validate(LedgerContext ctx) {
        TransactionType type = ctx.type();
        String reversesTransactionId = ctx.reversesTransactionId();

        if (type == TransactionType.REVERSAL && reversesTransactionId == null) {
            throw new InvalidReversalException("REVERSAL transaction must reference the transaction it reverses");
        }
        if (type != TransactionType.REVERSAL && reversesTransactionId != null) {
            throw new InvalidReversalException(
                    "Only REVERSAL transactions may set reversesTransactionId (was type " + type + ")");
        }
    }
}
