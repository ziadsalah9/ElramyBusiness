-- =============================================================
-- V20: suppliers and expenses.
-- -------------------------------------------------------------
-- * suppliers: who we buy from. New purchase invoices must name one
--   (old ones keep supplier_id NULL = "no supplier").
-- * supplier_payments: money paid to a supplier later, against what we
--   owe them (purchase totals - paid at purchase - these payments).
-- * expenses: rent, salaries, electricity... A cash expense, like a cash
--   supplier payment, comes out of the drawer on its day.
-- Additive only; idempotent.
-- =============================================================

CREATE TABLE IF NOT EXISTS suppliers (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    phone      VARCHAR(30),
    address    VARCHAR(500),
    notes      VARCHAR(500),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_suppliers_name ON suppliers (LOWER(TRIM(name)));

ALTER TABLE purchase_invoices ADD COLUMN IF NOT EXISTS supplier_id BIGINT REFERENCES suppliers (id);
CREATE INDEX IF NOT EXISTS idx_purchase_invoices_supplier ON purchase_invoices (supplier_id);

CREATE TABLE IF NOT EXISTS supplier_payments (
    id             BIGSERIAL PRIMARY KEY,
    payment_number VARCHAR(30)    NOT NULL,
    supplier_id    BIGINT         NOT NULL REFERENCES suppliers (id),
    branch_id      BIGINT REFERENCES branches (id),
    amount         NUMERIC(38, 2) NOT NULL,
    payment_method VARCHAR(30)    NOT NULL,
    notes          VARCHAR(500),
    paid_by        BIGINT REFERENCES users (id),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    CONSTRAINT uq_supplier_payments_number UNIQUE (payment_number),
    CONSTRAINT chk_supplier_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_supplier_payments_method CHECK (payment_method IN ('CASH', 'VISA', 'INSTAPAY', 'VODAFONE_CASH'))
);
CREATE INDEX IF NOT EXISTS idx_supplier_payments_supplier ON supplier_payments (supplier_id);
CREATE INDEX IF NOT EXISTS idx_supplier_payments_created ON supplier_payments (branch_id, created_at);

CREATE TABLE IF NOT EXISTS expenses (
    id             BIGSERIAL PRIMARY KEY,
    expense_number VARCHAR(30)    NOT NULL,
    category       VARCHAR(30)    NOT NULL,
    description    VARCHAR(500),
    amount         NUMERIC(38, 2) NOT NULL,
    payment_method VARCHAR(30)    NOT NULL,
    expense_date   DATE           NOT NULL,
    branch_id      BIGINT REFERENCES branches (id),
    created_by     BIGINT REFERENCES users (id),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    CONSTRAINT uq_expenses_number UNIQUE (expense_number),
    CONSTRAINT chk_expenses_amount CHECK (amount > 0),
    CONSTRAINT chk_expenses_method CHECK (payment_method IN ('CASH', 'VISA', 'INSTAPAY', 'VODAFONE_CASH'))
);
CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses (branch_id, expense_date);
