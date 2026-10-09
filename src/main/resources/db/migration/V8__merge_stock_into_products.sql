-- =============================================================
-- V8: stock quantity lives on the product.
-- -------------------------------------------------------------
-- The store is single-branch (V7), so a product has one quantity.
-- products.quantity becomes the source of truth and the stock
-- movement ledger references the product directly.
--
-- Nothing is dropped:
--   * product_stocks is renamed to product_stocks_archive; existing
--     foreign keys follow the rename, so history stays consistent;
--   * the old product_stock_id columns are kept (unused) as history.
-- Guarded: a no-op on fresh databases and on re-runs.
-- =============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'public' AND table_name = 'products') THEN
        RETURN;
    END IF;

    ALTER TABLE products ADD COLUMN IF NOT EXISTS quantity INTEGER NOT NULL DEFAULT 0;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'products_quantity_nonneg') THEN
        ALTER TABLE products ADD CONSTRAINT products_quantity_nonneg CHECK (quantity >= 0);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'product_stocks') THEN

        -- Every branch row; the closed branch is already 0 after V7.
        UPDATE products p
           SET quantity = s.total
          FROM (SELECT product_id, SUM(quantity)::INTEGER AS total
                  FROM product_stocks GROUP BY product_id) s
         WHERE s.product_id = p.id;

        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'public' AND table_name = 'stock_transactions') THEN
            ALTER TABLE stock_transactions ADD COLUMN IF NOT EXISTS product_id BIGINT;
            UPDATE stock_transactions st
               SET product_id = ps.product_id
              FROM product_stocks ps
             WHERE ps.id = st.product_stock_id AND st.product_id IS NULL;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'public' AND table_name = 'branch_transfer_items') THEN
            ALTER TABLE branch_transfer_items ADD COLUMN IF NOT EXISTS product_id BIGINT;
            UPDATE branch_transfer_items bti
               SET product_id = ps.product_id
              FROM product_stocks ps
             WHERE ps.id = bti.product_stock_id AND bti.product_id IS NULL;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = 'public' AND table_name = 'product_stocks_archive') THEN
            ALTER TABLE product_stocks RENAME TO product_stocks_archive;
        END IF;
    END IF;

    -- Ledger -> product link (also on databases that never had product_stocks).
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'stock_transactions') THEN
        ALTER TABLE stock_transactions ADD COLUMN IF NOT EXISTS product_id BIGINT;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_transactions_product_fk') THEN
            ALTER TABLE stock_transactions
                ADD CONSTRAINT stock_transactions_product_fk FOREIGN KEY (product_id) REFERENCES products(id);
        END IF;
        CREATE INDEX IF NOT EXISTS idx_stock_transactions_product ON stock_transactions (product_id);
    END IF;
END $$;
