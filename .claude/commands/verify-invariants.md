Scan the entire codebase and verify all domain invariants from CLAUDE.md are enforced:

Does any code path construct a Transaction without validating that postings sum to zero?
Is float or double used anywhere in the money path (including tests)?
Does Account have a balance field anywhere (domain model, entity, DTO)?
Are there any update or delete operations on Transaction or Posting?
Is sign logic (debit/credit) duplicated outside the balance computation method?
Can a posting be created with a currency that doesn't match its account?
Does every write endpoint accept and enforce an idempotency key?
Are jOOQ generated classes imported outside of the store/ package?
Is DSLContext injected anywhere outside JooqLedgerStore?
Are there any JPA/Hibernate imports anywhere in the project?

Report violations with file and line number. If no violations found, confirm clean.
