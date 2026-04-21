CREATE TABLE postings (
    id              VARCHAR(32) PRIMARY KEY,
    transaction_id  VARCHAR(32) NOT NULL REFERENCES transactions(id),
    account_id      VARCHAR(32) NOT NULL REFERENCES accounts(id),
    amount          BIGINT NOT NULL,
    currency        CHAR(3) NOT NULL,
    sequence_no     BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (account_id, sequence_no)
);

CREATE INDEX idx_postings_account_id ON postings(account_id, sequence_no DESC);
CREATE INDEX idx_postings_transaction_id ON postings(transaction_id);
