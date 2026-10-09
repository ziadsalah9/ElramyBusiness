-- =============================================================
-- V21: installment sales (تقسيط).
-- -------------------------------------------------------------
-- A plan belongs to one sales invoice: what was not paid at the sale
-- (principal) plus a markup % (total) is split into monthly
-- installments. The markup is added to what the customer owes. Customer
-- payments are applied to the oldest unpaid installment first
-- (paid_amount); a return credited on the invoice shortens the plan
-- from its last installment. Additive only; idempotent.
-- =============================================================

CREATE TABLE IF NOT EXISTS installment_plans (
    id             BIGSERIAL PRIMARY KEY,
    invoice_id     BIGINT         NOT NULL REFERENCES sales_invoices (id),
    customer_id    BIGINT         NOT NULL REFERENCES customers (id),
    principal      NUMERIC(38, 2) NOT NULL,
    markup_percent NUMERIC(5, 2)  NOT NULL,
    markup_amount  NUMERIC(38, 2) NOT NULL,
    total_amount   NUMERIC(38, 2) NOT NULL,
    months         INTEGER        NOT NULL,
    first_due_date DATE           NOT NULL,
    created_by     BIGINT REFERENCES users (id),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    CONSTRAINT uq_installment_plans_invoice UNIQUE (invoice_id),
    CONSTRAINT chk_installment_plans CHECK (principal > 0 AND markup_percent BETWEEN 0 AND 100 AND markup_amount >= 0
        AND total_amount = principal + markup_amount AND months BETWEEN 1 AND 60)
);
CREATE INDEX IF NOT EXISTS idx_installment_plans_customer ON installment_plans (customer_id);

CREATE TABLE IF NOT EXISTS installments (
    id          BIGSERIAL PRIMARY KEY,
    plan_id     BIGINT         NOT NULL REFERENCES installment_plans (id) ON DELETE CASCADE,
    seq         INTEGER        NOT NULL,
    due_date    DATE           NOT NULL,
    amount      NUMERIC(38, 2) NOT NULL,
    paid_amount NUMERIC(38, 2) NOT NULL DEFAULT 0,
    paid_at     TIMESTAMP,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT uq_installments_seq UNIQUE (plan_id, seq),
    CONSTRAINT chk_installments_amounts CHECK (amount >= 0 AND paid_amount >= 0 AND paid_amount <= amount)
);
CREATE INDEX IF NOT EXISTS idx_installments_due ON installments (due_date);
CREATE INDEX IF NOT EXISTS idx_installments_plan ON installments (plan_id);
