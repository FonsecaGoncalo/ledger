# API contract

Defined in `api/` controllers + `api/dto/`. Error mapping in
`GlobalExceptionHandler`. OpenAPI available at `/v3/api-docs`, Swagger UI at
`/swagger-ui.html` (Springdoc).

## Endpoints

```
POST   /api/v1/accounts                      create account          → 201
GET    /api/v1/accounts                      list, cursor-paginated  → 200
GET    /api/v1/accounts/{id}                 get account             → 200
GET    /api/v1/accounts/{id}/balance         derived balance         → 200
GET    /api/v1/accounts/{id}/transactions    posting history         → 200

POST   /api/v1/transactions                  submit transaction      → 201
GET    /api/v1/transactions/{id}             get transaction         → 200
```

## Wire format

- **Amounts**: `long` minor units. €100.00 = `10000`.
- **Currency**: ISO 4217, uppercase, e.g. `"EUR"`. Validated on request.
- **Timestamps**: ISO 8601 UTC, e.g. `"2026-01-15T10:30:00Z"`.
- **IDs**: KSUID — time-ordered, base62, with entity prefix (`acct_`, `txn_`,
  `pst_`). Stored `VARCHAR(32)`.
- **Pagination**: cursor-based. `?cursor=X&limit=N`. Default 50, clamped to [1, 200].
- **Idempotency**: `idempotencyKey` in request body (not header).

## Requests and responses

```json
// POST /api/v1/accounts
{
  "externalRef": "merchant-123",
  "type": "LIABILITY",
  "currency": "EUR",
  "ownerName": "Café Lisboa",
  "metadata": { "country": "PT" }
}

// POST /api/v1/transactions
{
  "idempotencyKey": "pay-merchant-123-20260115-001",
  "type": "DEPOSIT",
  "description": "Card payment - terminal T456",
  "reversesTransactionId": null,
  "postings": [
    { "accountId": "acct_...", "amount": 10000,  "currency": "EUR" },
    { "accountId": "acct_...", "amount": -10000, "currency": "EUR" }
  ],
  "metadata": { "terminalId": "T456" }
}

// GET /api/v1/transactions/{id}
{
  "transactionId": "txn_...",
  "type": "DEPOSIT",
  "description": "Card payment - terminal T456",
  "idempotencyKey": "pay-merchant-123-20260115-001",
  "reversesTransactionId": null,
  "postings": [
    { "postingId": "pst_...", "accountId": "acct_...", "amount": 10000,  "currency": "EUR", "sequenceNo": 42 },
    { "postingId": "pst_...", "accountId": "acct_...", "amount": -10000, "currency": "EUR", "sequenceNo": 17 }
  ],
  "metadata": { "terminalId": "T456" },
  "createdAt": "2026-01-15T10:30:00Z"
}

// GET /api/v1/accounts/{id}/balance
{
  "accountId": "acct_...",
  "balance": 10000,
  "currency": "EUR",
  "asOf": "2026-01-15T10:30:00Z",
  "postingCount": 42
}

// GET /api/v1/accounts/{id}/transactions?cursor=...&limit=20
{
  "accountId": "acct_...",
  "items": [
    {
      "transactionId": "txn_...",
      "type": "DEPOSIT",
      "description": "Card payment - terminal T456",
      "postings": [ { "postingId": "pst_...", "accountId": "acct_...", "amount": 10000, "currency": "EUR", "sequenceNo": 42 } ],
      "metadata": {},
      "createdAt": "2026-01-15T10:30:00Z"
    }
  ],
  "nextCursor": "eyJzZXEiOjQxfQ==",
  "hasMore": true
}
```

## Error shape (every non-2xx)

```json
{
  "error": "INSUFFICIENT_FUNDS",
  "message": "Account acct_... has insufficient funds",
  "details": { "accountId": "acct_...", "requested": 50000, "available": 30000 },
  "timestamp": "2026-01-15T10:30:00Z",
  "traceId": "..."
}
```

## Error codes (from `GlobalExceptionHandler`)

| code                           | status | when                                           |
|--------------------------------|--------|------------------------------------------------|
| `VALIDATION_ERROR`             | 400    | bean-validation failures (`@NotBlank`, etc.)   |
| `BAD_REQUEST`                  | 400    | `IllegalArgumentException`                     |
| `ACCOUNT_NOT_FOUND`            | 404    | unknown account id                             |
| `TRANSACTION_NOT_FOUND`        | 404    | unknown transaction id                         |
| `ORIGINAL_TRANSACTION_NOT_FOUND` | 404  | reversal targets a missing transaction         |
| `DUPLICATE_ACCOUNT`            | 409    | `externalRef` collision                        |
| `DUPLICATE_TRANSACTION`        | 409    | idempotency key reused with different payload  |
| `INSUFFICIENT_FUNDS`           | 409    | write would drive balance negative             |
| `ALREADY_REVERSED`             | 409    | original already has a reversal                |
| `INVALID_TRANSACTION`          | 422    | unbalanced postings, malformed structure       |
| `CURRENCY_MISMATCH`            | 422    | posting currency ≠ account currency            |
| `INVALID_REVERSAL`             | 422    | type/link mismatch, non-mirror postings        |

Idempotent replay of an already-recorded transaction returns the original body
with **200**, not 201.
