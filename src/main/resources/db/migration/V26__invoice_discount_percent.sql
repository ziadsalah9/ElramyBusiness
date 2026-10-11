-- =============================================================
-- V26: invoice discount given as a percentage.
-- -------------------------------------------------------------
-- discount stays the amount actually taken off (what totals, reports and
-- the cash closing use); discount_percent records the percentage it was
-- computed from, when the cashier entered one, for the receipt and the
-- invoice details. NULL = the discount was entered as an amount.
-- Idempotent.
-- =============================================================

ALTER TABLE sales_invoices ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5, 2);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_sales_invoices_discount_percent') THEN
        ALTER TABLE sales_invoices ADD CONSTRAINT ck_sales_invoices_discount_percent
            CHECK (discount_percent IS NULL OR (discount_percent >= 0 AND discount_percent <= 100));
    END IF;
END $$;
