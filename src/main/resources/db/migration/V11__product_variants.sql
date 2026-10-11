-- =============================================================
-- V11: product variants (colour x size), each with its own stock,
-- code and barcode. One price per product; low stock on the total.
-- -------------------------------------------------------------
-- Every existing product becomes one product with ONE variant
-- carrying its current colour, size, quantity and barcode; the
-- variant code is the product code + '-1'. Invoice lines and the
-- stock ledger get a variant_id pointing at that variant.
-- Nothing is dropped: products.color/size/quantity/barcode stay as
-- unused legacy columns. Guarded: no-op on fresh DBs and re-runs.
-- =============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'public' AND table_name = 'products') THEN
        RETURN;
    END IF;

    CREATE TABLE IF NOT EXISTS product_variants (
        id          BIGSERIAL PRIMARY KEY,
        product_id  BIGINT NOT NULL REFERENCES products(id),
        color       VARCHAR(50),
        size        VARCHAR(50),
        code        VARCHAR(255) NOT NULL UNIQUE,
        barcode     VARCHAR(255) UNIQUE,
        quantity    INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
        active      BOOLEAN NOT NULL DEFAULT TRUE,
        created_at  TIMESTAMP,
        updated_at  TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_product_variants_product ON product_variants (product_id);
    -- Same colour/size only once per product (NULL = "no colour"/"no size").
    CREATE UNIQUE INDEX IF NOT EXISTS uq_product_variants_combo
        ON product_variants (product_id, COALESCE(color, ''), COALESCE(size, ''));

    -- One variant per existing product.
    INSERT INTO product_variants (product_id, color, size, code, barcode, quantity, active, created_at, updated_at)
    SELECT p.id, NULLIF(btrim(p.color), ''), NULLIF(btrim(p.size), ''), p.code || '-1', p.barcode,
           COALESCE(p.quantity, 0), TRUE, now(), now()
      FROM products p
     WHERE NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id);

    -- Link invoice lines and the ledger to the variant.
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'sales_invoice_items') THEN
        ALTER TABLE sales_invoice_items ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES product_variants(id);
        UPDATE sales_invoice_items i SET variant_id = v.id
          FROM product_variants v WHERE v.product_id = i.product_id AND i.variant_id IS NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'purchase_invoice_items') THEN
        ALTER TABLE purchase_invoice_items ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES product_variants(id);
        UPDATE purchase_invoice_items i SET variant_id = v.id
          FROM product_variants v WHERE v.product_id = i.product_id AND i.variant_id IS NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'stock_transactions') THEN
        ALTER TABLE stock_transactions ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES product_variants(id);
        UPDATE stock_transactions t SET variant_id = v.id
          FROM product_variants v WHERE v.product_id = t.product_id AND t.variant_id IS NULL;
        CREATE INDEX IF NOT EXISTS idx_stock_transactions_variant ON stock_transactions (variant_id);
    END IF;

    -- The legacy products.quantity is no longer maintained; drop its
    -- NOT NULL so new products don't need it (the column itself stays).
    ALTER TABLE products ALTER COLUMN quantity DROP NOT NULL;
END $$;
