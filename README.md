# Ledger

A double-entry ledger service. Tracks who has what.

Balances aren't stored — they're derived by summing an append-only posting log. Every write is idempotent, and every transaction balances to zero per currency. Those two properties are the whole reason this thing is interesting; everything below is the mechanism for making them true.

## Quick start

```
docker compose up -d
open http://localhost:8080/swagger-ui.html
```

Postgres and the app on `localhost:8080`. Flyway runs migrations on boot. Full running instructions are at the bottom.

## Scope

In scope:

- Double-entry postings across any combination of account types and currencies
- Atomic transactions that balance to zero per currency
- Full reversals that reference a prior transaction
- Caller-supplied idempotency, replay-safe
- Current balance and full posting history per account

Out of scope, deliberately:

- **FX / currency conversion.** Multi-currency transactions are allowed, but each currency has to balance independently. Conversion is the caller's concern.
- **Partial reversals.** A `REVERSAL` fully negates the original. Partial undos are expressed as compensating transactions of a different type — that names what actually happened.
- **Holds, authorizations, pending postings.** Every posting is final the moment it lands.
- **Interest, fees, scheduled actions.** No time-driven behaviour; the ledger does nothing on its own.
- **Account closure, type/currency changes.** Accounts are immutable once opened.
- **Point-in-time balances.** "As of now" only.
- **Authentication, multi-tenancy.** Idempotency keys live in a single global keyspace.

## Domain

### Entities

```
┌─────────────────────┐         ┌─────────────────────┐        ┌─────────────────────┐
│    Transaction      │ 1..*    │      Posting        │  *..1  │      Account        │
│─────────────────────│◀───────▶│─────────────────────│◀──────▶│─────────────────────│
│ id              str │ contains│ account         ref │applies │ externalRef  string │
│ type            enum│  ≥ 2    │ amount  int64 signed│   to   │ type            enum│
│ description  string?│         │ currency     iso4217│        │ currency     iso4217│
│ reverses     txn_id?│         └─────────────────────┘        │ metadata        json│
│ idempotencyKey   str│                                        └─────────────────────┘
│ metadata        json│
└─────────────────────┘
```

**Account** — a named holder of value in a single currency. Its `type` (`ASSET`, `LIABILITY`, `EXPENSE`, `REVENUE`) governs how its balance is displayed. Immutable once opened.

**Posting** — a signed amount against one account. Postings are atomic, never modified, never deleted. An account's balance is the sum of its postings.

**Transaction** — a group of ≥2 postings committed atomically. Carries a type, a caller-supplied idempotency key, optional metadata, and, for reversals, a reference to the original transaction.

### Balances are derived

There's no balance column anywhere. The balance of an account is `sum(postings.amount)`, flipped by a sign convention that depends on account type:

| Account type | Visible balance | Example |
| --- | --- | --- |
| `ASSET`, `EXPENSE` — debit-normal | `Σ postings` | raw `+100` → `+100` |
| `LIABILITY`, `REVENUE` — credit-normal | `−Σ postings` | raw `−100` → `+100` |

Postings themselves are always raw signed integers. Because a balanced transaction sums to zero, the sign flip is a display concern only — it stays accounting-correct for any combination of account types.

Example: a customer deposits 100 USD at a bank.

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

Every transaction submission has to satisfy:

1. At least two postings, summing to zero within each currency. Multi-currency transactions are allowed, but each currency balances independently; the ledger never implicitly converts.
2. Every posting's currency matches its account's. USD accounts don't see EUR postings.
3. No account ends up with a negative visible balance.
4. If the transaction is a reversal, it matches the reversal shape below.

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

Two payloads are equal when `type`, `description`, `reverses`, `metadata` (structural compare), and postings all match. Posting order doesn't matter, but each `(account, amount, currency)` tuple appears the same number of times.

### Concurrency

Concurrent transactions that touch overlapping accounts serialize via row-level locks on `account`, acquired in ascending account-id order before validation runs. That ordering makes deadlocks between overlapping transactions structurally impossible, and it gives the solvency check a consistent view of the balance for the duration of the transaction. Postgres runs at `READ COMMITTED` — lock discipline, not isolation level, is what enforces the invariants.

Idempotency-key races are resolved by a unique constraint on `transaction.idempotency_key`. The loser of a race with an identical payload returns the winning transaction; the loser with a differing payload gets a `409`.

## Design decisions

Non-obvious choices and what they trade away.

**Derived balances, not materialised.** Every read recomputes from the posting log. Reads cost `O(postings-per-account)`, which is fine at this scale; past some threshold this becomes a balance-snapshot table updated by an async projector, with the posting log still the source of truth. Keeping it derived here means there's exactly one place balances can go wrong and no reconciliation job to own.

**Raw signed integers on postings, sign flip at display.** The alternative is signing by account type at write time. That's messier: the same deposit looks different depending on where it lands, and a reversal has to invert per-account. With raw integers, "balanced" is the literal zero-sum arithmetic check, reversals are a multiset negation, and the sign convention only surfaces at the display boundary.

**Caller-supplied idempotency keys.** Server-generated keys mean the caller needs a round-trip to learn them, which defeats the point under retry. Callers already have natural unique identifiers for most operations (payment id, order id, webhook event id); they supply those and we store the verbatim payload for later equality checks.

**Minor units as int64, no decimals.** Removes every rounding question from the ledger. Callers convert at the edge once, and every arithmetic operation inside the service is integer. FX, when it happens, is a caller concern for the same reason.

**Per-currency balance, not cross-currency.** A multi-currency transaction is legal as long as each currency sums to zero independently. The ledger never implies a rate. The moment it did, it would own FX — and FX is a rate-fetching, rate-dating, rate-auditing problem that belongs in a different service.

## Examples

Four scenarios against a running service. Each one has a runnable script under `scripts/`:

```
./scripts/personal-accounts-demo.sh   # deposit, transfer, withdraw
./scripts/overdraft-demo.sh           # solvency rejection
./scripts/reversal-demo.sh            # reversal
./scripts/fx-conversion-demo.sh       # caller-implemented FX
```

All take an optional `BASE_URL` (default `http://localhost:8080`) and need `curl` and `jq`.

### Deposit, transfer, withdraw

Open a settlement pool (`ASSET`) and two customer accounts (`LIABILITY`), all in EUR:

```
POST /api/v1/accounts  { "type": "ASSET",     "currency": "EUR", "externalRef": "settlement-..."    }
POST /api/v1/accounts  { "type": "LIABILITY", "currency": "EUR", "externalRef": "personal-alice-..." }
POST /api/v1/accounts  { "type": "LIABILITY", "currency": "EUR", "externalRef": "personal-bob-..."   }
```

Deposit €1,000 into Alice — settlement debited, Alice's liability credited:

```
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

```
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

```
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
| --- | --- | --- | --- |
| Deposit €1,000 → Alice | €1,000.00 | €1,000.00 | €0.00 |
| Transfer €750 Alice → Bob | €1,000.00 | €250.00 | €750.00 |
| Withdraw €300 from Bob | €700.00 | €250.00 | €450.00 |

### Overdraft rejected

Alice has €200. She tries to withdraw €500:

```
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

### Reversal

Alice has €1,000 and transfers €400 to Bob. Mistake — undo it by referencing the original transaction with the posting signs flipped:

```
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

### Caller-implemented FX

The ledger doesn't convert currencies, but one transaction can span multiple as long as each currency balances independently. That's the shape of caller-implemented FX.

Four accounts — EUR and USD treasuries (`ASSET`), Alice's EUR and USD sides (`LIABILITY`). Alice has €100 and wants USD at 0.87. One transaction, four postings: two EUR legs that sum to zero, two USD legs that sum to zero.

```
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

## Running it

### Prerequisites

- Java 25 on `PATH` (or let the Gradle toolchain fetch it)
- Docker — for Postgres and the Testcontainers-backed integration tests

### Start the service

Full stack via Compose — Postgres and the app, exposed on `localhost:8080`:

```
docker compose up -d
docker compose logs -f app
docker compose down
```

To iterate on the app against a containerised Postgres:

```
docker compose up -d postgres
./gradlew bootRun
```

Defaults match `docker-compose.yml`: db `ledger`, user `ledger`, password `ledger`, port `5432`. Flyway runs migrations on startup.

### API docs

With the service running, the OpenAPI spec and an interactive UI are served by the app:

| URL | What |
| --- | --- |
| `http://localhost:8080/swagger-ui.html` | Interactive Swagger UI — try endpoints against the running app |
| `http://localhost:8080/v3/api-docs` | OpenAPI spec (JSON) |
| `http://localhost:8080/v3/api-docs.yaml` | OpenAPI spec (YAML) |

### Tests

| Suite | Scope | Docker |
| --- | --- | --- |
| `test` | Unit — domain logic and validators | No |
| `integrationTest` | API and scenario tests against a real Postgres via Testcontainers | Yes |

```
./gradlew test                                     # unit only
./gradlew integrationTest                          # integration only
./gradlew check                                    # both, plus other verification

./gradlew test --tests "*TransactionValidatorSpec"
./gradlew integrationTest --tests "*SubmitTransactionSpec"
```

Integration tests spin up an ephemeral Postgres per run. Docker daemon needs to be reachable.

### Regenerate jOOQ sources

Classes under `io.gfonseca.ledger.generated.*` are generated from the Flyway migrations in `src/main/resources/db/migration/`. After editing any migration:

```
./gradlew generateJooq
```