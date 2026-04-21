# Testing

## Framework

Spock 2.4 on Groovy 4.0. No JUnit/AssertJ code — Spock's `given/when/then` +
`expect` blocks + power assertions only. Testcontainers Postgres for anything
that touches the DB.

## Suite layout (Gradle JVM test suites, `build.gradle`)

- **`test`** — `src/test/groovy`. Fast, no Spring, no DB. Domain units:
  rule specs, `MoneySpec`.
- **`integrationTest`** — `src/integrationTest/groovy`. `@SpringBootTest` +
  Testcontainers (`postgres:17-alpine`, shared per class, TRUNCATE between tests
  via `AbstractIntegrationSpec`). Runs via `./gradlew integrationTest`, also
  wired into `check`.

No H2, no embedded DBs. Same engine as production.

## What lives where

**`src/test/groovy`** (unit)
- `domain/MoneySpec` — minor-unit arithmetic, overflow.
- `domain/validation/rule/*Spec` — each `LedgerRule` gets its own spec,
  positive and negative cases. Covers balance-sum, currency match, sufficient
  funds, reversal type/link, reversal mirror.

**`src/integrationTest/groovy`** (integration)
- `AbstractIntegrationSpec` — base class, starts Testcontainers, truncates
  tables between tests, exposes `mockMvc` + `jdbcTemplate`.
- `LedgerServiceSpec` — deposits, withdrawals, reversals, idempotency.
- `LedgerServiceConcurrencySpec` — 10 threads race to withdraw full balance;
  exactly one succeeds. Proves Postgres row locking.
- `api/AccountControllerSpec`, `api/TransactionControllerSpec` — full HTTP
  via MockMvc: happy paths, every error code, idempotency replay, pagination.
- `scenario/*Spec` — end-to-end business narratives (e.g.
  `CardPaymentScenarioSpec`). Self-contained, readable cold.

## Conventions

- Spec names read as sentences: `"rejects withdrawal exceeding balance"()`.
- One reason to fail per feature method.
- No shared mutable state across specs.
- No `@Ignore`, no `@PendingFeature` on green paths.
- Assertions: Spock power assertions (`then: x == y`), not AssertJ.
- Balances asserted via the balance endpoint or `LedgerService.getBalance`,
  not by summing rows in SQL.
- Money in tests is `Money`/`long` minor units — never `double`.
