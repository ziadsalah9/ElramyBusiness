-- =============================================================
-- V5: retire the stock-adjustment feature.
-- -------------------------------------------------------------
-- The feature (entity, endpoints, screen) was removed. Its table is
-- archived rather than dropped, so existing adjustment records are
-- not lost and the change can be reversed with a single RENAME.
-- The stock movements those adjustments produced stay in
-- stock_transactions, and product_stocks quantities are untouched.
-- Guarded so it is safe on fresh databases and on re-runs.
-- =============================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'stock_adjustments'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'stock_adjustments_archive'
    ) THEN
        ALTER TABLE stock_adjustments RENAME TO stock_adjustments_archive;
    END IF;
END $$;
