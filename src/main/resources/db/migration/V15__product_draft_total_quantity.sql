-- =============================================================
-- V15: total available quantity on incomplete products.
-- -------------------------------------------------------------
-- The product form now has a separate "total available quantity":
-- colours/sizes may add up to at most the total, and the rest is kept
-- as "no colour or size". Drafts store the total (required to complete
-- them). Existing drafts whose every colour/size has a quantity get
-- total = their sum; the others stay empty and show it as missing.
-- Additive; idempotent.
-- =============================================================

ALTER TABLE product_drafts ADD COLUMN IF NOT EXISTS total_quantity INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_product_drafts_total_quantity') THEN
        ALTER TABLE product_drafts
            ADD CONSTRAINT chk_product_drafts_total_quantity CHECK (total_quantity IS NULL OR total_quantity >= 0);
    END IF;
END $$;

UPDATE product_drafts d
SET total_quantity = s.total
FROM (
    SELECT draft_id, SUM(quantity) AS total
    FROM product_draft_variants
    GROUP BY draft_id
    HAVING COUNT(*) = COUNT(quantity)          -- every row has a quantity
) s
WHERE s.draft_id = d.id
  AND d.total_quantity IS NULL;
