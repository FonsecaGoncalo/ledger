# Ledger

A double-entry ledger service. Tracks who has what.

Balances aren't stored — they're derived by summing an append-only posting log. Every write is idempotent, and every transaction balances to zero per currency. Those two properties are the whole reason this thing is interesting; everything below is the mechanism for making them true.

## Contents

1. [Running it](#1-running-it)
2. [Scope](#2-scope)
3. [Domain](#3-Domain)

---

## 1. Running it

### Prerequisites

- Java 25 on the `PATH` (or let the Gradle toolchain fetch it)
- Docker — for Postgres and the Testcontainers-backed integration tests

### Start the service

Full stack via Compose — Postgres and the app, exposed on `localhost:8080`:

```bash
docker compose up -d
docker compose logs -f app
docker compose down
```

To iterate on the app against a containerised Postgres:

```bash
docker compose up -d postgres
./gradlew bootRun
```

Defaults match `docker-compose.yml`: db `ledger`, user `ledger`, password `ledger`, port `5432`. Flyway runs migrations on startup.

### API docs

With the service running, the OpenAPI spec and an interactive UI are served by the app:

| URL | What |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interactive Swagger UI — try endpoints against the running app |
| `http://localhost:8080/v3/api-docs` | OpenAPI spec (JSON) |
| `http://localhost:8080/v3/api-docs.yaml` | OpenAPI spec (YAML) |

### Tests

Two Gradle suites:

| Suite | Scope | Docker |
|---|---|---|
| `test` | Unit — domain logic and validators | No |
| `integrationTest` | API and scenario tests against a real Postgres via Testcontainers | Yes |

```bash
./gradlew test                                     # unit only
./gradlew integrationTest                          # integration only
./gradlew check                                    # both, plus other verification

./gradlew test --tests "*TransactionValidatorSpec"
./gradlew integrationTest --tests "*SubmitTransactionSpec"
```

Integration tests spin up an ephemeral Postgres per run. Docker daemon needs to be reachable.

### Regenerate jOOQ sources

Classes under `io.gfonseca.ledger.generated.*` are generated from the Flyway migrations in `src/main/resources/db/migration/`. After editing any migration:

```bash
./gradlew generateJooq
```

---

## 2. Scope

### In scope

- Double-entry postings across any combination of account types and currencies
- Atomic and balanced transactions
- Full reversals that reference a prior transaction
- Caller-supplied idempotency, replay-safe
- Current balance and full posting history per account

### Out of scope

- **FX / currency conversion.** Multi-currency transactions are allowed, but each currency has to balance independently.
- **Partial reversals.** A `REVERSAL` has to fully negate the original. If you need a partial undo, post a compensating transaction of another type.
- **Holds, authorizations, pending postings.** Every posting is final the moment it lands.
- **Interest, fees, scheduled actions.** No time-driven behaviour. The ledger does nothing on its own.
- **Account closure, type/currency changes.** Accounts are immutable once opened.
- **Point-in-time balances.** Only "as of now".
- **Authentication, multi-tenancy.** Idempotency keys live in a single global keyspace.

---

## 3. Domain

### Entities

```
┌─────────────────────┐        ┌─────────────────────┐        ┌─────────────────────┐
│    Transaction      │ 1..*   │      Posting        │  *..1  │      Account        │
│─────────────────────│◀──────▶│─────────────────────│◀──────▶│─────────────────────│
│ id              uuid│ contains│ account         ref │applies │ externalRef  string │
│ type            enum│  ≥ 2   │ amount  int64 signed│   to   │ type            enum│
│ description  string?│        │ currency     iso4217│        │ currency     iso4217│
│ reverses     txn_id?│        └─────────────────────┘        │ metadata        json│
│ idempotencyKey   str│                                       └─────────────────────┘
│ metadata        json│
└─────────────────────┘
```

**Account** — a named holder of value in a single currency. Its `type` (`ASSET`, `LIABILITY`, `EXPENSE`, `REVENUE`) governs how its balance is displayed. Immutable once opened.

**Posting** — a signed amount against one account. Postings are atomic, never modified, never deleted. An account's balance is the sum of its postings.

**Transaction** — a group of ≥2 postings committed atomically. Carries a type, a caller-supplied idempotency key, optional metadata, and, for reversals, a reference to the original transaction.

### Balances are derived

There's no balance column anywhere. The balance of an account is `sum(postings.amount)`, flipped by a sign convention that depends on account type:

| Account type | Visible balance | Example |
|---|---|---|
| `ASSET`, `EXPENSE` — debit-normal | `Σ postings` | raw `+100` → `+100` |
| `LIABILITY`, `REVENUE` — credit-normal | `−Σ postings` | raw `−100` → `+100` |

Postings themselves are always raw signed integers. Because a balanced transaction sums to zero, the sign flip is just a display concern, it's accounting-correct for any combination of account types.

Example: a customer deposits 100 USD at a bank:

```
┌────────────────────────────────────┬────────────────────────────────────┐
│ Customer Deposits (Liability · USD)│ Bank Cash       (Asset     · USD)  │
├────────────────────────────────────┼────────────────────────────────────┤
│ Δ post ……… raw signed     −100.00  │ Δ post ……… raw signed     +100.00  │
│ After  ……… raw sum        −100.00  │ After  ……… raw sum        +100.00  │
├────────────────────────────────────┼────────────────────────────────────┤
│ Visible: credit-normal → negate    │ Visible: debit-normal → as-is      │
│                     +100.00 USD    │                     +100.00 USD    │
└────────────────────────────────────┴────────────────────────────────────┘

Check:  (−100) + (+100) = 0.00 USD  ✓
```

Both sides see `+100`. Postings sum to zero.

### Valid transaction rules

Every transaction submission has to satisfy the checks below.

1. At least two postings, and they sum to zero within each currency. Multi-currency transactions are allowed, but each currency has to balance independently, the ledger never implicitly converts.
2. Every posting's currency matches its account's. USD accounts don't see EUR postings.
3. No account ends up with a negative visible balance.
4. If the transaction is a reversal, it has to match the reversal shape — see below.

#### Reversals

Reversals point backwards at existing state, so they have tighter rules than other transactions:

```
┌─────────────────────────────┐              ┌─────────────────────────────┐
│ Original transaction        │              │ Reversal transaction        │
│ type: TRANSFER              │   reverses   │ type: REVERSAL              │
│─────────────────────────────│  ─────────▶  │─────────────────────────────│
│  Alice  — USD      −25.00   │              │  Alice  — USD      +25.00   │
│  Bob    — USD      +25.00   │              │  Bob    — USD      −25.00   │
└─────────────────────────────┘              └─────────────────────────────┘
```

- Exactly one `reverses` target.
- The original can't itself be a `REVERSAL` — no chains.
- Postings have to be an exact negation: same accounts, same currencies, multiset-equal amounts with opposite sign. No partials.
- A second attempt to reverse the same original is rejected.

#### Duplicate submissions

Every transaction submission carries a caller-supplied idempotency key. Three outcomes:

```
Case A — First submission (key unseen)
    submit(key: K, payload: P) ──▶ store(K → P) ──▶ commit          [CREATED]

Case B — Replay (key seen, payload equal)
    submit(key: K, payload: P) ──▶ lookup(K) == P ──▶ return original
                                                                    [IDEMPOTENT HIT]

Case C — Conflict (key seen, payload differs)
    submit(key: K, payload: P') ─▶ lookup(K) ≠ P' ──▶ reject        [409 CONFLICT]
```

Two payloads are equal when `type`, `description`, `reverses`, `metadata` (structural compare), and postings all match. Posting order doesn't matter, but each `(account, amount, currency)` tuple has to appear the same number of times.

### Examples

Four scenarios against a running service. Each one has a runnable script under `scripts/`:

```bash
./scripts/personal-accounts-demo.sh   # deposit, transfer, withdraw
./scripts/overdraft-demo.sh           # solvency rejection
./scripts/reversal-demo.sh            # reversal
./scripts/fx-conversion-demo.sh       # caller-implemented FX
```

All take an optional `BASE_URL` (default `http://localhost:8080`) and need `curl` and `jq`.

#### Deposit, transfer, withdraw

Open a settlement pool (`ASSET`) and two customer accounts (`LIABILITY`), all in EUR:

```http
POST /api/v1/accounts  { "type": "ASSET",     "currency": "EUR", "externalRef": "settlement-..."    }
POST /api/v1/accounts  { "type": "LIABILITY", "currency": "EUR", "externalRef": "personal-alice-..." }
POST /api/v1/accounts  { "type": "LIABILITY", "currency": "EUR", "externalRef": "personal-bob-..."   }
```

Deposit €1,000 into Alice — settlement debited, Alice's liability credited:

```http
POST /api/v1/transactions
{
  "idempotencyKey": "deposit-alice-...",
  "type": "DEPOSIT",
  "postings": [
    { "accountId": "<settlement>", "amount":  100000, "currency": "EUR" },
    { "accountId": "<alice>",      "amount": -100000, "currency": "EUR" }
  ]
}
```

Transfer €750 Alice → Bob. Both are `LIABILITY` accounts, so the same sign convention applies to both legs — Alice's is positive (her visible balance decreases), Bob's is negative (his increases):

```http
POST /api/v1/transactions
{
  "type": "TRANSFER",
  "postings": [
    { "accountId": "<alice>", "amount":  75000, "currency": "EUR" },
    { "accountId": "<bob>",   "amount": -75000, "currency": "EUR" }
  ]
}
```

Bob withdraws €300 — mirror of the deposit:

```http
POST /api/v1/transactions
{
  "type": "WITHDRAWAL",
  "postings": [
    { "accountId": "<bob>",        "amount":  30000, "currency": "EUR" },
    { "accountId": "<settlement>", "amount": -30000, "currency": "EUR" }
  ]
}
```

Visible balances after each step:

| After | Settlement | Alice | Bob |
|---|---|---|---|
| Deposit €1,000 → Alice | €1,000.00 | €1,000.00 | €0.00 |
| Transfer €750 Alice → Bob | €1,000.00 | €250.00 | €750.00 |
| Withdraw €300 from Bob | €700.00 | €250.00 | €450.00 |

#### Overdraft rejected

Alice has €200. She tries to withdraw €500:

```http
POST /api/v1/transactions
{
  "type": "WITHDRAWAL",
  "postings": [
    { "accountId": "<alice>",      "amount":  50000, "currency": "EUR" },
    { "accountId": "<settlement>", "amount": -50000, "currency": "EUR" }
  ]
}

← HTTP 409
{
  "error": "INSUFFICIENT_FUNDS",
  "details": { "requested": 50000, "available": 20000 }
}
```

Nothing is persisted. Solvency rejected at validation.

#### Reversal

Alice has €1,000 and transfers €400 to Bob. Mistake — undo it by referencing the original transaction with the posting signs flipped:

```http
POST /api/v1/transactions
{
  "idempotencyKey": "reversal-transfer-...",
  "type": "REVERSAL",
  "reversesTransactionId": "txn_3Cfl198gFP4jbr7TMbzwPHAyJWc",
  "postings": [
    { "accountId": "<alice>", "amount": -40000, "currency": "EUR" },
    { "accountId": "<bob>",   "amount":  40000, "currency": "EUR" }
  ]
}
```

Final: Alice €1,000, Bob €0. The original transfer is still in history — a reversal is a canceling entry, not an erasure.

#### Caller-implemented FX

The ledger doesn't convert currencies, but one transaction can span multiple as long as each currency balances independently. That's the shape of caller-implemented FX.

Four accounts — EUR and USD treasuries (`ASSET`), Alice's EUR and USD sides (`LIABILITY`). Alice has €100 and wants USD at 0.87. One transaction, four postings: two EUR legs that sum to zero, two USD legs that sum to zero.

```http
POST /api/v1/transactions
{
  "idempotencyKey": "fx-alice-eur-to-usd-...",
  "type": "TRANSFER",
  "description": "FX conversion: Alice €100.00 → $87.00",
  "postings": [
    { "accountId": "<alice-eur>",    "amount":  10000, "currency": "EUR" },
    { "accountId": "<eur-treasury>", "amount": -10000, "currency": "EUR" },
    { "accountId": "<usd-treasury>", "amount":   8700, "currency": "USD" },
    { "accountId": "<alice-usd>",    "amount":  -8700, "currency": "USD" }
  ],
  "metadata": { "rate": "0.87", "pair": "EUR/USD" }
}
```

Per-currency check: EUR `(+10000) + (−10000) = 0` ✓, USD `(+8700) + (−8700) = 0` ✓. The ledger commits. It doesn't interpret the rate or validate the rounding — those are the caller's problem. The ledger owns conservation.

Final: Alice EUR €0, Alice USD $87.