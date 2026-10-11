-- =============================================================
-- V16: sales returns, customer payments, cash part of payments.
-- -------------------------------------------------------------
-- * sales_invoices.cash_amount: the cash part of what was paid at the
--   sale (CASH = paid, MIXED = typed by the cashier, others = 0). Old
--   MIXED invoices stay NULL (unknown) and count as 0, as before.
-- * sales_invoices.returned_total / sales_invoice_items.returned_quantity:
--   kept in step by every return, so screens need no extra lookups.
-- * sales_returns (+ items): a partial or full return of a sale. The
--   value goes back as cash and/or as credit against what the customer
--   still owes.
-- * customer_payments: money a customer pays later against their balance.
-- Additive only; idempotent.
-- =============================================================

ALTER TABLE sales_invoices ADD COLUMN IF NOT EXISTS cash_amount NUMERIC(38, 2);
ALTER TABLE sales_invoices ADD COLUMN IF NOT EXISTS returned_total NUMERIC(38, 2) NOT NULL DEFAULT 0;
ALTER TABLE sales_invoice_items ADD COLUMN IF NOT EXISTS returned_quantity INTEGER NOT NULL DEFAULT 0;

UPDATE sales_invoices
SET cash_amount = CASE WHEN payment_method = 'CASH' THEN paid ELSE 0 END
WHERE cash_amount IS NULL AND (payment_method IS NULL OR payment_method <> 'MIXED');

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sales_invoices_returns') THEN
        ALTER TABLE sales_invoices ADD CONSTRAINT chk_sales_invoices_returns
            CHECK (returned_total >= 0 AND returned_total <= total AND (cash_amount IS NULL OR (cash_amount >= 0 AND cash_amount <= paid)));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sales_invoice_items_returned') THEN
        ALTER TABLE sales_invoice_items ADD CONSTRAINT chk_sales_invoice_items_returned
            CHECK (returned_quantity >= 0 AND returned_quantity <= quantity);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS sales_returns (
    id            BIGSERIAL PRIMARY KEY,
    return_number VARCHAR(30)    NOT NULL,
    invoice_id    BIGINT         NOT NULL REFERENCES sales_invoices (id),
    customer_id   BIGINT REFERENCES customers (id),
    branch_id     BIGINT REFERENCES branches (id),
    total         NUMERIC(38, 2) NOT NULL,
    cash_refund   NUMERIC(38, 2) NOT NULL,
    credit_amount NUMERIC(38, 2) NOT NULL,
    reason        VARCHAR(500),
    created_by    BIGINT REFERENCES users (id),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    CONSTRAINT uq_sales_returns_number UNIQUE (return_number),
    CONSTRAINT chk_sales_returns_amounts CHECK (total > 0 AND cash_refund >= 0 AND credit_amount >= 0
        AND cash_refund + credit_amount = total)
);
CREATE INDEX IF NOT EXISTS idx_sales_returns_invoice ON sales_returns (invoice_id);
CREATE INDEX IF NOT EXISTS idx_sales_returns_customer ON sales_returns (customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_returns_created ON sales_returns (branch_id, created_at);

CREATE TABLE IF NOT EXISTS sales_return_items (
    id              BIGSERIAL PRIMARY KEY,
    return_id       BIGINT         NOT NULL REFERENCES sales_returns (id) ON DELETE CASCADE,
    invoice_item_id BIGINT         NOT NULL REFERENCES sales_invoice_items (id),
    product_id      BIGINT         NOT NULL REFERENCES products (id),
    variant_id      BIGINT REFERENCES product_variants (id),
    quantity        INTEGER        NOT NULL,
    unit_refund     NUMERIC(38, 2) NOT NULL,
    total           NUMERIC(38, 2) NOT NULL,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT chk_sales_return_items CHECK (quantity > 0 AND unit_refund >= 0 AND total >= 0)
);
CREATE INDEX IF NOT EXISTS idx_sales_return_items_return ON sales_return_items (return_id);

CREATE TABLE IF NOT EXISTS customer_payments (
    id             BIGSERIAL PRIMARY KEY,
    payment_number VARCHAR(30)    NOT NULL,
    customer_id    BIGINT         NOT NULL REFERENCES customers (id),
    branch_id      BIGINT REFERENCES branches (id),
    amount         NUMERIC(38, 2) NOT NULL,
    payment_method VARCHAR(30)    NOT NULL,
    notes          VARCHAR(500),
    received_by    BIGINT REFERENCES users (id),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    CONSTRAINT uq_customer_payments_number UNIQUE (payment_number),
    CONSTRAINT chk_customer_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_customer_payments_method CHECK (payment_method IN ('CASH', 'VISA', 'INSTAPAY', 'VODAFONE_CASH'))
);
CREATE INDEX IF NOT EXISTS idx_customer_payments_customer ON customer_payments (customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_payments_created ON customer_payments (branch_id, created_at);

-- Cash closing sums the day's sales per branch.
CREATE INDEX IF NOT EXISTS idx_sales_invoices_branch_created ON sales_invoices (branch_id, created_at);

-- Store settings (single row): what receipts print, and the employee
-- discount limit. Editable by admins in the app.
CREATE TABLE IF NOT EXISTS store_settings (
    id                            BIGINT PRIMARY KEY,
    store_name                    VARCHAR(200) NOT NULL,
    phone                         VARCHAR(100),
    address                       VARCHAR(300),
    receipt_footer                VARCHAR(300),
    employee_max_discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 10,
    created_at                    TIMESTAMP,
    updated_at                    TIMESTAMP,
    CONSTRAINT chk_store_settings_single CHECK (id = 1),
    CONSTRAINT chk_store_settings_discount CHECK (employee_max_discount_percent BETWEEN 0 AND 100)
);
INSERT INTO store_settings (id, store_name, receipt_footer, employee_max_discount_percent, created_at, updated_at)
VALUES (1, 'مفروشات الرامي', 'شكراً لزيارتكم', 10, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
