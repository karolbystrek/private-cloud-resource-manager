ALTER TABLE wallets
    RENAME COLUMN balance_cu TO balance_credits;
ALTER TABLE wallets
    ALTER COLUMN balance_credits TYPE BIGINT USING ROUND(balance_credits)::BIGINT;
ALTER TABLE wallets
    ALTER COLUMN balance_credits SET DEFAULT 0;
ALTER TABLE wallets
    DROP CONSTRAINT check_positive_balance;
ALTER TABLE wallets
    ADD CONSTRAINT check_non_negative_credits CHECK (balance_credits >= 0);

ALTER TABLE jobs
    RENAME COLUMN total_cost_cu TO total_cost_credits;
ALTER TABLE jobs
    ALTER COLUMN total_cost_credits TYPE BIGINT USING ROUND(total_cost_credits)::BIGINT;
ALTER TABLE jobs
    ALTER COLUMN total_cost_credits SET DEFAULT 0;

ALTER TABLE cu_ledger
    RENAME TO credit_registry;
ALTER TABLE credit_registry
    RENAME COLUMN amount_cu TO amount_credits;
ALTER TABLE credit_registry
    ALTER COLUMN amount_credits TYPE BIGINT USING ROUND(amount_credits)::BIGINT;

ALTER INDEX idx_cu_ledger_wallet_id RENAME TO idx_credit_registry_wallet_id;
ALTER INDEX idx_cu_ledger_job_id RENAME TO idx_credit_registry_job_id;
