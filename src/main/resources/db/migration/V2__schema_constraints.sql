-- =============================================================
-- V2: schema hardening (constraints + indexes)
-- -------------------------------------------------------------
-- Guarded, idempotent migration.
--
-- Flyway runs BEFORE Hibernate. On an existing database the tables
-- are present, so this migration adds the missing integrity
-- constraints and indexes. On a brand-new database the tables do
-- not exist yet (Hibernate creates them afterwards), so every block
-- checks for table existence and silently skips. Re-running is safe
-- because each object is created only when absent.
-- =============================================================

-- =========================================================
-- PRODUCT STOCKS: remove the duplicate unique constraint.
-- An identical unique now exists under the readable name
-- product_stocks_product_id_branch_id_key, so this one is
-- redundant. It is a constraint (not a bare index), so it
-- must be dropped as a constraint.
-- =========================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'product_stocks'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'ukmtsjosgtfjifp9jqby01ocd2l'
              AND conrelid = 'public.product_stocks'::regclass
        ) THEN
            ALTER TABLE product_stocks
                DROP CONSTRAINT ukmtsjosgtfjifp9jqby01ocd2l;
        END IF;
    END IF;
END $$;


-- =========================================================
-- CASH CLOSINGS: one closing per branch per day
-- =========================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'cash_closings'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'cash_closings_branch_date_key'
        ) THEN
            ALTER TABLE cash_closings
                ADD CONSTRAINT cash_closings_branch_date_key
                UNIQUE (branch_id, closing_date);
        END IF;
    END IF;

END $$;


DO $$
BEGIN

    -- =========================================================
    -- CHECK CONSTRAINTS: non-negative quantities and money
    -- =========================================================

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='product_stocks') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='product_stocks_quantity_nonneg') THEN
            ALTER TABLE product_stocks ADD CONSTRAINT product_stocks_quantity_nonneg CHECK (quantity >= 0);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='product_stocks_minqty_nonneg') THEN
            ALTER TABLE product_stocks ADD CONSTRAINT product_stocks_minqty_nonneg CHECK (minimum_quantity >= 0);
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='product_prices') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='product_prices_nonneg') THEN
            ALTER TABLE product_prices ADD CONSTRAINT product_prices_nonneg CHECK (purchase_price >= 0 AND selling_price >= 0);
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='sales_invoices') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='sales_invoices_nonneg') THEN
            ALTER TABLE sales_invoices ADD CONSTRAINT sales_invoices_nonneg CHECK (total >= 0 AND paid >= 0 AND discount >= 0);
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='sales_invoice_items') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='sales_invoice_items_valid') THEN
            ALTER TABLE sales_invoice_items ADD CONSTRAINT sales_invoice_items_valid CHECK (quantity > 0 AND unit_price >= 0 AND total >= 0 AND discount >= 0);
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='purchase_invoices') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='purchase_invoices_nonneg') THEN
            ALTER TABLE purchase_invoices ADD CONSTRAINT purchase_invoices_nonneg CHECK (total >= 0 AND paid >= 0 AND discount >= 0);
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='purchase_invoice_items') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='purchase_invoice_items_valid') THEN
            ALTER TABLE purchase_invoice_items ADD CONSTRAINT purchase_invoice_items_valid CHECK (quantity > 0 AND unit_price >= 0 AND total >= 0 AND discount >= 0);
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='stock_transactions') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='stock_transactions_quantity_pos') THEN
            ALTER TABLE stock_transactions ADD CONSTRAINT stock_transactions_quantity_pos CHECK (quantity > 0);
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='branch_transfer_items') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='branch_transfer_items_quantity_pos') THEN
            ALTER TABLE branch_transfer_items ADD CONSTRAINT branch_transfer_items_quantity_pos CHECK (quantity > 0);
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='stock_adjustments') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='stock_adjustments_quantities_nonneg') THEN
            ALTER TABLE stock_adjustments ADD CONSTRAINT stock_adjustments_quantities_nonneg CHECK (system_quantity >= 0 AND actual_quantity >= 0);
        END IF;
    END IF;

END $$;


DO $$
BEGIN

    -- =========================================================
    -- ONE ACTIVE PRICE PER PRODUCT
    -- =========================================================
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='product_prices') THEN
        CREATE UNIQUE INDEX IF NOT EXISTS product_prices_one_active
            ON product_prices (product_id)
            WHERE active IS TRUE;
    END IF;

END $$;


DO $$
BEGIN

    -- =========================================================
    -- FOREIGN-KEY INDEXES
    -- PostgreSQL does not index foreign keys automatically; without
    -- these, parent deletes and joins do sequential scans.
    -- =========================================================

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='product_stocks') THEN
        CREATE INDEX IF NOT EXISTS idx_product_stocks_branch ON product_stocks (branch_id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='product_prices') THEN
        CREATE INDEX IF NOT EXISTS idx_product_prices_product ON product_prices (product_id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='sales_invoices') THEN
        CREATE INDEX IF NOT EXISTS idx_sales_invoices_branch ON sales_invoices (branch_id);
        CREATE INDEX IF NOT EXISTS idx_sales_invoices_customer ON sales_invoices (customer_id);
        CREATE INDEX IF NOT EXISTS idx_sales_invoices_cashier ON sales_invoices (cashier_id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='sales_invoice_items') THEN
        CREATE INDEX IF NOT EXISTS idx_sales_items_invoice ON sales_invoice_items (invoice_id);
        CREATE INDEX IF NOT EXISTS idx_sales_items_product ON sales_invoice_items (product_id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='purchase_invoices') THEN
        CREATE INDEX IF NOT EXISTS idx_purchase_invoices_branch ON purchase_invoices (branch_id);
        CREATE INDEX IF NOT EXISTS idx_purchase_invoices_creator ON purchase_invoices (created_by);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='purchase_invoice_items') THEN
        CREATE INDEX IF NOT EXISTS idx_purchase_items_invoice ON purchase_invoice_items (invoice_id);
        CREATE INDEX IF NOT EXISTS idx_purchase_items_product ON purchase_invoice_items (product_id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='stock_transactions') THEN
        CREATE INDEX IF NOT EXISTS idx_stock_tx_stock ON stock_transactions (product_stock_id);
        CREATE INDEX IF NOT EXISTS idx_stock_tx_reference ON stock_transactions (reference_type, reference_id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='stock_adjustments') THEN
        CREATE INDEX IF NOT EXISTS idx_stock_adjustments_stock ON stock_adjustments (product_stock_id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='branch_transfers') THEN
        CREATE INDEX IF NOT EXISTS idx_branch_transfers_from ON branch_transfers (from_branch_id);
        CREATE INDEX IF NOT EXISTS idx_branch_transfers_to ON branch_transfers (to_branch_id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='branch_transfer_items') THEN
        CREATE INDEX IF NOT EXISTS idx_branch_transfer_items_transfer ON branch_transfer_items (transfer_id);
        CREATE INDEX IF NOT EXISTS idx_branch_transfer_items_stock ON branch_transfer_items (product_stock_id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='cash_closings') THEN
        CREATE INDEX IF NOT EXISTS idx_cash_closings_branch ON cash_closings (branch_id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='users') THEN
        CREATE INDEX IF NOT EXISTS idx_users_branch ON users (branch_id);
    END IF;

END $$;