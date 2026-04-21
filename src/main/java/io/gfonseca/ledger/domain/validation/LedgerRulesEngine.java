package io.gfonseca.ledger.domain.validation;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LedgerRulesEngine {

    private final List<LedgerRule> rules;

    public LedgerRulesEngine(List<LedgerRule> rules) {
        this.rules = rules;
    }

    public void validate(LedgerContext ctx) {
        rules.forEach(rule -> rule.validate(ctx));
    }
}
