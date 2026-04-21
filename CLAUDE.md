# CLAUDE.md

Guidance for Claude Code working in this repo.

## Project

`io.gfonseca:ledger` — a double-entry ledger service for recording money movements.
Every business event (card payment, fee, payout, reversal) produces a Transaction
composed of ≥2 Postings that sum to zero per currency. Balance is derived from the
posting log, never stored. `postings` is the single source of truth.

## Tech stack

Java 25 · Spring Boot 4.0.5 · Gradle 9.4.1 · PostgreSQL 17 · jOOQ (no JPA) · Flyway ·
Spock 2.4 on Groovy 4 · Testcontainers · Micrometer/Prometheus · Springdoc OpenAPI.

## Commands

```bash
./gradlew build                    # compile + unit + integration (Docker required)
./gradlew test                     # unit specs (src/test, Spock, no DB)
./gradlew integrationTest          # integration specs (src/integrationTest, Testcontainers)
./gradlew test --tests "io.gfonseca.ledger.domain.MoneySpec"
./gradlew generateJooq             # regenerate jOOQ sources from Flyway migrations
./gradlew bootRun                  # run app (needs Postgres — docker compose up -d)
./gradlew spotlessApply            # format Java + Groovy
```

## Critical boundaries

- `DSLContext` injected ONLY into `JooqLedgerStore`.
- `io.gfonseca.ledger.generated.*` imports ONLY in `store/`.
- Domain records are the language of `LedgerStore` — not jOOQ records, not DTOs.
- Controllers never import from `store/`. Service never imports from `api/`.

## Domain invariants — non-negotiable

Violating any of these is a bug. Each rule is enforced by a `LedgerRule` in
`domain/validation/rule/`, wired through `LedgerRulesEngine` inside
`LedgerService.submitTransaction`. `/verify-invariants` scans for violations.

### 1. Double-entry: postings sum to zero
≥2 Postings per Transaction. Sum of `amount` is zero per currency.
`BalancedPostingsRule` → `InvalidTransactionException`.

### 2. Signed amounts: positive = debit, negative = credit
- `ASSET`, `EXPENSE`: debit-normal — positive posting increases balance.
- `LIABILITY`, `REVENUE`: credit-normal — negative posting increases balance.

Sign convention is applied in exactly one place: `AccountType.isDebitNormal()` used
by `JooqLedgerStore.getBalances` / `getBalanceView`. Do not duplicate this logic.

### 3. Immutable postings
`Transaction` and `Posting` are records. No update or delete — corrections are
`REVERSAL` transactions. `LedgerStore` exposes only inserts and reads.

### 4. Balance is derived, never stored
No `balance` column on `accounts`. Computed as `COALESCE(SUM(amount), 0)` over
`postings`, inside the same DB transaction as the row lock on the write path.

### 5. Money is never float/double
`Money` uses `long` minor units. `BIGINT` in Postgres. No `float`/`double` anywhere
in the money path, including test assertions.

### 6. Single-currency accounts
Currency set at creation, immutable. Posting currency must match account currency.
`CurrencyMatchRule` enforces it; `currency` column on both tables backs it.

### 7. Idempotency on all writes
`SubmitTransactionRequest.idempotencyKey` is required. `IdempotencyService` checks
BEFORE validation; duplicates with matching payload return the original (200), a
mismatched replay throws `DuplicateTransactionException`. UNIQUE constraint on
`transactions.idempotency_key` backs the application check.

### 8. Per-account ordering
Postings have monotonic `sequence_no` per account, assigned inside the locked
transaction. Cursor pagination uses it. UNIQUE `(account_id, sequence_no)`.

### 9. Reversals are full mirrors, at most once
`type = REVERSAL` MUST carry `reversesTransactionId`; any other type MUST NOT
(`ReversalTypeLinkRule`). A reversal's postings are an exact multiset mirror:
same accounts, same currencies, amounts negated — no partial reversals
(`ReversalMirrorRule`). One original, at most one reversal; a reversal cannot
be reversed. Enforced in-app and by partial UNIQUE index on
`transactions.reverses_transaction_id` (V004).

## Concurrency rule

Every balance check that informs a write decision happens inside the same
`@Transactional` boundary as the write, under `SELECT ... FOR UPDATE` on affected
accounts, locked in UUID order. No optimistic concurrency, no app-level locks, no
retry loops. Detail: `.claude/docs/architecture.md`.

## Code conventions

- Records for value objects and DTOs. Classes only for stateful things.
- Constructor injection only. No `@Autowired` on fields.
- No Lombok. No JPA, no Hibernate, no Spring Data. jOOQ only.
- No `Optional` as field or parameter. Return type only.
- No wildcard imports.
- Spock spec names read as sentences: `"rejects withdrawal exceeding balance"()`.
- Domain exceptions extend sealed `LedgerException`.
- Controllers return DTOs; `GlobalExceptionHandler` maps exceptions to HTTP.
  No `ResponseEntity` with hardcoded status in controllers.
- Production code in `src/main/java` (Palantir-formatted). Tests in
  `src/test/groovy` and `src/integrationTest/groovy`.

## Reference docs

Read on demand — don't preload unless the task touches the area.

- **Package layout, schema notes, concurrency detail** → `.claude/docs/architecture.md`
- **HTTP endpoints, JSON shapes, error codes** → `.claude/docs/api-contract.md`
- **Test strategy, suites, what's where** → `.claude/docs/testing.md`
- **Schema source of truth** → `src/main/resources/db/migration/`
