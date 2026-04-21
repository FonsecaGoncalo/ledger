Create a new business scenario test following the project conventions.
The test should:

Live in `src/integrationTest/groovy/io/gfonseca/ledger/scenario/` as
`<Name>ScenarioSpec.groovy`.
Extend `AbstractIntegrationSpec` (gives `@SpringBootTest`, Testcontainers Postgres,
per-test TRUNCATE, `mockMvc`, `jdbcTemplate`).
Be a self-contained narrative: setup accounts, perform operations via MockMvc,
verify outcomes. Readable by someone who has never seen the codebase.
Drive HTTP through `mockMvc.perform(...)`; do not call services directly.
Assert balances via `GET /api/v1/accounts/{id}/balance`, not by summing rows in SQL.
Verify the double-entry invariant where relevant: sum of all posting amounts
across all accounts = 0 per currency.
Use Spock idioms: `given:`/`when:`/`then:` blocks, power assertions (`then: x == y`).
No AssertJ, no JUnit.
Money is `long` minor units — never `double`.
Feature-method names read as sentences: `"settles a card payment and posts the fee"()`.

Ask me what business scenario to test, then generate the complete spec.
