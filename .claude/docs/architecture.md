# Architecture

## Package structure

```
io.gfonseca.ledger/
├── LedgerApplication.java
├── config/                         # Spring config (Flyway, OpenAPI) — infra only
├── domain/
│   ├── model/                      # Account, AccountType, Accounts, BalanceView,
│   │                               # KsuidGenerator, Money, PagedResult, Posting,
│   │                               # PostingRequest, Sequences, Transaction, TransactionType
│   ├── service/                    # LedgerService (orchestration, @Transactional),
│   │                               # AccountsService, IdempotencyService, TransactionFactory
│   ├── validation/                 # LedgerRule, LedgerRulesEngine, LedgerContext
│   │   └── rule/                   # BalancedPostingsRule, CurrencyMatchRule,
│   │                               # SufficientFundsRule, ReversalTypeLinkRule,
│   │                               # ReversalMirrorRule (ordered via @Order)
│   └── exception/                  # sealed LedgerException hierarchy
├── store/
│   ├── LedgerStore.java            # interface — speaks domain records
│   ├── JooqLedgerStore.java        # sole holder of DSLContext + generated types
│   └── RecordMapper.java           # jOOQ ↔ domain, one place
└── api/
    ├── AccountController.java, TransactionController.java
    ├── dto/                        # request/response records
    ├── mapper/DtoMapper.java       # domain ↔ DTO, one place
    └── GlobalExceptionHandler.java # sealed exceptions → HTTP status
```

Migrations: `src/main/resources/db/migration/V{N}__*.sql`.

## Why this shape

- `domain/` is pure Java — zero Spring core, only `@Component`/`@Transactional`
  stereotypes where a bean needs to exist. No jOOQ imports.
- `store/` is the persistence seam. Generated classes (`io.gfonseca.ledger.generated.*`)
  never leak outside it.
- `api/` is HTTP glue. DTOs decoupled from domain records so wire and model evolve
  independently.
- No `util/`, no `security/`, no `entity/`, no multi-module.

## Build pipeline

1. Flyway migration SQL defines the schema (source of truth).
2. `generateJooq` parses the SQL via `DDLDatabase` — no running database needed.
3. Application compiles against generated types. Schema drift is a compile error.

Generated code: `build/generated-sources/jooq/`, never committed.

## Schema notes

- `amount BIGINT` signed, not `NUMERIC`. Minor units as integers.
- `currency` on both `accounts` and `postings` — redundant by design; postings are
  self-describing. `CurrencyMatchRule` enforces equality.
- No `balance` column on `accounts` — load-bearing decision.
- `sequence_no` per-account monotonic, assigned inside the locked write path.
- `metadata JSONB` on accounts and transactions — extensible without migrations.
- IDs: KSUID, stored `VARCHAR(32)`, with entity prefixes (`acct_`, `txn_`, `pst_`).
- V004 adds partial UNIQUE index on `transactions.reverses_transaction_id`.

## Concurrency model

Concurrency is Postgres, not application code.

- `@Transactional` on `LedgerService.submitTransaction()`.
- `AccountsService.loadAndLock` → `LedgerStore.findAndLockAccounts` issues
  `SELECT ... FOR UPDATE` on affected accounts, **sorted by UUID** to prevent
  deadlocks when a transaction touches multiple accounts.
- `SufficientFundsRule` reads balances inside the same transaction, after the lock.
- Idempotency: application check in `IdempotencyService.find` runs before validation,
  belt-and-braces with UNIQUE constraint on `transactions.idempotency_key`.

Rule: every balance check that informs a write decision happens inside the same DB
transaction as the write, under a row lock. No optimistic concurrency, no
application-level locks, no retry loops.
