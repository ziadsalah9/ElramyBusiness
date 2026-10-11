-- =============================================================
-- V12: incomplete products (drafts) can hold several colour/size
-- rows, each with its own quantity - same shape as a product's
-- variants (V11). Each existing draft gets one row with its current
-- colour, size and quantity. product_drafts.color/size/quantity stay
-- as unused legacy columns. Guarded and additive.
-- =============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'public' AND table_name = 'product_drafts') THEN
        RETURN;
    END IF;

    CREATE TABLE IF NOT EXISTS product_draft_variants (
        id          BIGSERIAL PRIMARY KEY,
        draft_id    BIGINT NOT NULL REFERENCES product_drafts(id) ON DELETE CASCADE,
        color       VARCHAR(50),
        size        VARCHAR(50),
        quantity    INTEGER CHECK (quantity IS NULL OR quantity >= 0),
        source_row  INTEGER,
        created_at  TIMESTAMP,
        updated_at  TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_product_draft_variants_draft ON product_draft_variants (draft_id);

    INSERT INTO product_draft_variants (draft_id, color, size, quantity, source_row, created_at, updated_at)
    SELECT d.id, d.color, d.size, d.quantity, d.source_row, now(), now()
      FROM product_drafts d
     WHERE NOT EXISTS (SELECT 1 FROM product_draft_variants v WHERE v.draft_id = d.id);
END $$;
