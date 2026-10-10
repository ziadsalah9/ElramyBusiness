-- =============================================================
-- V25: colours managed by admins, like product types (V24).
-- -------------------------------------------------------------
-- product_colors holds the names (and an optional #rrggbb swatch for the
-- dropdowns). Colour/size rows and draft rows reference a colour by its
-- name with ON UPDATE CASCADE, so renaming a colour renames it everywhere.
-- A colour in use cannot be deleted (FK), only hidden (active).
-- The old "متعدد" value becomes the label it was shown as, "متعدد الألوان".
-- The unmapped legacy columns products.color / product_drafts.color are
-- left as they are. Idempotent.
-- =============================================================

CREATE TABLE IF NOT EXISTS product_colors (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    hex        VARCHAR(7),
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_product_colors_name UNIQUE (name),
    CONSTRAINT ck_product_colors_hex CHECK (hex IS NULL OR hex ~ '^#[0-9a-f]{6}$')
);

INSERT INTO product_colors (name, hex, active, created_at, updated_at)
SELECT v.name, v.hex, TRUE, NOW(), NOW()
FROM (VALUES ('أبيض', '#ffffff'), ('بيج', '#d9c3a0'), ('رمادي', '#9ca3af'), ('أسود', '#111111'),
             ('كحلي', '#1e2a5a'), ('أحمر', '#dc2626'), ('أخضر', '#16a34a'), ('زيتي', '#6b7a2e'),
             ('بني', '#7c4a1e'), ('وردي', '#f472b6'), ('أصفر', '#facc15'), ('برتقالي', '#f97316'),
             ('بنفسجي', '#8b5cf6'), ('فيروزي', '#14b8a6'), ('ذهبي', '#c9a227'), ('فضي', '#c0c0c0'),
             ('متعدد الألوان', NULL)) AS v(name, hex)
ON CONFLICT (name) DO NOTHING;

UPDATE product_variants       SET color = 'متعدد الألوان' WHERE color = 'متعدد';
UPDATE product_draft_variants SET color = 'متعدد الألوان' WHERE color = 'متعدد';

-- Blank means "no colour"; extra spaces would break the link below.
UPDATE product_variants       SET color = NULLIF(regexp_replace(TRIM(color), '\s+', ' ', 'g'), '') WHERE color IS NOT NULL;
UPDATE product_draft_variants SET color = NULLIF(regexp_replace(TRIM(color), '\s+', ' ', 'g'), '') WHERE color IS NOT NULL;

-- Any value already in use becomes a colour, so the links below always hold.
INSERT INTO product_colors (name, active, created_at, updated_at)
SELECT DISTINCT color, TRUE, NOW(), NOW() FROM product_variants WHERE color IS NOT NULL
ON CONFLICT (name) DO NOTHING;
INSERT INTO product_colors (name, active, created_at, updated_at)
SELECT DISTINCT color, TRUE, NOW(), NOW() FROM product_draft_variants WHERE color IS NOT NULL
ON CONFLICT (name) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_variants_color') THEN
        ALTER TABLE product_variants ADD CONSTRAINT fk_product_variants_color
            FOREIGN KEY (color) REFERENCES product_colors (name) ON UPDATE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_draft_variants_color') THEN
        ALTER TABLE product_draft_variants ADD CONSTRAINT fk_product_draft_variants_color
            FOREIGN KEY (color) REFERENCES product_colors (name) ON UPDATE CASCADE;
    END IF;
END $$;
