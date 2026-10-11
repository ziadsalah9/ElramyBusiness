-- =============================================================
-- V24: product types managed by admins (were a fixed list in code).
-- -------------------------------------------------------------
-- product_types holds the names; products and drafts reference a type by
-- its name with ON UPDATE CASCADE, so renaming a type renames it on every
-- product. A type in use cannot be deleted (FK), only hidden (active).
-- The old "OTHER" value becomes the Arabic "أخرى" it was shown as.
-- Idempotent.
-- =============================================================

CREATE TABLE IF NOT EXISTS product_types (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_product_types_name UNIQUE (name)
);

INSERT INTO product_types (name, active, created_at, updated_at)
SELECT v.name, TRUE, NOW(), NOW()
FROM (VALUES ('لحاف'), ('بطانيه'), ('طقم'), ('فوط'), ('دفاية'), ('برنس'), ('مفارش'), ('حافظة'),
             ('مشاية'), ('كوفرتة'), ('مصلية'), ('نيش'), ('سفرة'), ('انترية'), ('سراحة'), ('أخرى')) AS v(name)
ON CONFLICT (name) DO NOTHING;

-- The fixed list goes (it would forbid every new type).
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT conrelid::regclass AS tbl, conname FROM pg_constraint
             WHERE contype = 'c' AND conrelid IN ('products'::regclass, 'product_drafts'::regclass)
               AND pg_get_constraintdef(oid) ILIKE '%item_type%'
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', c.tbl, c.conname);
    END LOOP;
END $$;

UPDATE products       SET item_type = 'أخرى' WHERE item_type = 'OTHER';
UPDATE product_drafts SET item_type = 'أخرى' WHERE item_type = 'OTHER';
UPDATE stocktakes     SET scope_types = replace(scope_types, 'OTHER', 'أخرى') WHERE scope_types LIKE '%OTHER%';

-- Any value already in use becomes a type, so the links below always hold.
INSERT INTO product_types (name, active, created_at, updated_at)
SELECT DISTINCT TRIM(item_type), TRUE, NOW(), NOW() FROM products WHERE item_type IS NOT NULL AND TRIM(item_type) <> ''
ON CONFLICT (name) DO NOTHING;
INSERT INTO product_types (name, active, created_at, updated_at)
SELECT DISTINCT TRIM(item_type), TRUE, NOW(), NOW() FROM product_drafts WHERE item_type IS NOT NULL AND TRIM(item_type) <> ''
ON CONFLICT (name) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_item_type') THEN
        ALTER TABLE products ADD CONSTRAINT fk_products_item_type
            FOREIGN KEY (item_type) REFERENCES product_types (name) ON UPDATE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_drafts_item_type') THEN
        ALTER TABLE product_drafts ADD CONSTRAINT fk_product_drafts_item_type
            FOREIGN KEY (item_type) REFERENCES product_types (name) ON UPDATE CASCADE;
    END IF;
END $$;
