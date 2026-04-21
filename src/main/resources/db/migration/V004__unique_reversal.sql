CREATE UNIQUE INDEX uq_transactions_reverses_transaction_id
    ON transactions (reverses_transaction_id)
    WHERE reverses_transaction_id IS NOT NULL;