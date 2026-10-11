-- =============================================================
-- V10: incomplete products imported from Excel ("منتجات غير مكتملة").
-- -------------------------------------------------------------
-- A separate table on purpose: drafts must never reach sales,
-- purchases, low stock or any product list, and those only read
-- `products`. Completing a draft creates the real product and
-- deletes the draft. Every data column is nullable - a draft is by
-- definition missing something. Additive only.
-- =============================================================

CREATE TABLE IF NOT EXISTS product_drafts (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(200),
    item_type         VARCHAR(255),
    color             VARCHAR(50),
    size              VARCHAR(50),
    model             VARCHAR(100),
    quantity          INTEGER,
    minimum_quantity  INTEGER,
    purchase_price    NUMERIC(18,2),
    profit_percentage NUMERIC(7,2),
    selling_price     NUMERIC(18,2),
    import_notes      TEXT,
    source_file       VARCHAR(255),
    source_row        INTEGER,
    created_by        BIGINT,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_drafts_created_at ON product_drafts (created_at DESC);
