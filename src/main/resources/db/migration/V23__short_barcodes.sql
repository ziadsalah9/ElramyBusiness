-- =============================================================
-- V23: short barcodes.
-- -------------------------------------------------------------
-- Barcodes become 100000 + the variant id (100001, 100002, ...): short to
-- print and scan, and unique because ids are. The old 12-digit barcode
-- moves to legacy_barcode, which scanning still finds, so labels already
-- printed keep working. Idempotent: only rows not converted yet change.
-- =============================================================

ALTER TABLE product_variants ADD COLUMN IF NOT EXISTS legacy_barcode VARCHAR(30);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uq_product_variants_legacy_barcode') THEN
        CREATE UNIQUE INDEX uq_product_variants_legacy_barcode ON product_variants (legacy_barcode);
    END IF;
END $$;

UPDATE product_variants
SET legacy_barcode = barcode,
    barcode        = (100000 + id)::text
WHERE barcode IS NOT NULL
  AND barcode <> (100000 + id)::text
  AND legacy_barcode IS NULL;

UPDATE product_variants
SET barcode = (100000 + id)::text
WHERE barcode IS NULL;
