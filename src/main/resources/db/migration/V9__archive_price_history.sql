-- =============================================================
-- V9: retire the price-history log.
-- -------------------------------------------------------------
-- The current price lives on products (V6) and invoice lines keep
-- their own unit_price, so the log was write-only. It is archived,
-- not dropped: the existing rows stay readable and one RENAME brings
-- it back. Guarded: a no-op on fresh databases and on re-runs.
-- =============================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'product_price_history')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = 'public' AND table_name = 'product_price_history_archive') THEN
        ALTER TABLE product_price_history RENAME TO product_price_history_archive;
    END IF;
END $$;
