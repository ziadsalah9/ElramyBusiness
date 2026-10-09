-- =============================================================
-- V6: the current price lives on the product.
-- -------------------------------------------------------------
-- A product has exactly one current price, so it is now stored on
-- products (purchase/selling price, profit %). product_prices is
-- kept, renamed to product_price_history, as an append-only log of
-- price changes; it no longer drives anything. Nothing is dropped
-- except the "one active price" index, which only made sense while
-- that table held the current price.
-- profit_percentage is numeric(7,2): the old 5,2 capped it at 999.99.
-- Guarded: a no-op on fresh databases and on re-runs.
-- =============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'public' AND table_name = 'products') THEN
        RETURN;
    END IF;

    ALTER TABLE products ADD COLUMN IF NOT EXISTS purchase_price    NUMERIC(18,2);
    ALTER TABLE products ADD COLUMN IF NOT EXISTS selling_price     NUMERIC(18,2);
    ALTER TABLE products ADD COLUMN IF NOT EXISTS profit_percentage NUMERIC(7,2);
    ALTER TABLE products ADD COLUMN IF NOT EXISTS price_updated_at  TIMESTAMP;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'products_prices_nonneg') THEN
        ALTER TABLE products ADD CONSTRAINT products_prices_nonneg
            -- profit_percentage is deliberately unchecked: selling below
            -- cost (allowed after a confirmation) gives a negative profit.
            CHECK ((purchase_price IS NULL OR purchase_price >= 0)
               AND (selling_price IS NULL OR selling_price >= 0));
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'product_prices') THEN

        -- Copy the active price onto its product.
        UPDATE products p
           SET purchase_price    = pp.purchase_price,
               selling_price     = pp.selling_price,
               profit_percentage = pp.profit_percentage,
               price_updated_at  = COALESCE(pp.created_at, now())
          FROM product_prices pp
         WHERE pp.product_id = p.id
           AND pp.active IS TRUE
           AND p.selling_price IS NULL;

        DROP INDEX IF EXISTS product_prices_one_active;
        UPDATE product_prices SET active = FALSE WHERE active IS TRUE;

        IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = 'public' AND table_name = 'product_price_history') THEN
            ALTER TABLE product_prices RENAME TO product_price_history;
        END IF;
    END IF;
END $$;
