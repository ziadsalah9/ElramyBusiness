-- =============================================================
-- V22: exchanges (استبدال) = a return + a new sale, linked.
-- -------------------------------------------------------------
-- The return of an exchange points at the sales invoice of the new
-- items. Its credit pays for them; any rest is paid now / owed, or
-- refunded in cash. Additive only; idempotent.
-- =============================================================

ALTER TABLE sales_returns ADD COLUMN IF NOT EXISTS exchange_invoice_id BIGINT REFERENCES sales_invoices (id);
CREATE INDEX IF NOT EXISTS idx_sales_returns_exchange ON sales_returns (exchange_invoice_id);
