CREATE TABLE transactions (
    id                        VARCHAR(32) PRIMARY KEY,
    type                      VARCHAR(20) NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'FEE', 'REVERSAL')),
    description               TEXT,
    idempotency_key           VARCHAR(255) NOT NULL UNIQUE,
    reverses_transaction_id   VARCHAR(32) REFERENCES transactions(id),
    metadata                  JSONB NOT NULL DEFAULT '{}',
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_idempotency_key ON transactions(idempotency_key);
