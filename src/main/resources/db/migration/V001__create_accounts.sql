CREATE TABLE accounts (
    id            VARCHAR(32) PRIMARY KEY,
    external_ref  VARCHAR(255) NOT NULL UNIQUE,
    type          VARCHAR(20) NOT NULL CHECK (type IN ('ASSET', 'LIABILITY', 'REVENUE', 'EXPENSE')),
    currency      CHAR(3) NOT NULL,
    metadata      JSONB NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
