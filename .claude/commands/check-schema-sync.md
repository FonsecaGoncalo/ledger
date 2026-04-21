Verify that the Flyway migrations, jOOQ generated code, and domain model are in sync:

Run `./gradlew generateJooq` and check for errors.
Compare the Flyway schema with the jOOQ generated tables — any drift?
Check that every database column has a corresponding field in the domain record
(via `RecordMapper`).
Check that domain record field types match the column types:
  `String` ↔ `VARCHAR(N)` / `CHAR(N)` / `TEXT`
  `long`   ↔ `BIGINT`
  `Instant` ↔ `TIMESTAMPTZ`
  `Map<String, Object>` ↔ `JSONB`
IDs are KSUIDs stored as `VARCHAR(32)`, not `UUID`. Flag any `UUID` type in schema
or code.
Verify index coverage: every foreign key has an index, every query pattern used
in `JooqLedgerStore` has a supporting index (e.g. `idx_postings_account_id`,
`idx_transactions_idempotency_key`, `uq_transactions_reverses_transaction_id`).

Report any mismatches.
