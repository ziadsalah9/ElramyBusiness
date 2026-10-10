-- =============================================================
-- V27: short item codes - 6 characters, letters and digits only, and the
-- code of a colour/size is also its barcode.
-- -------------------------------------------------------------
-- * elramy_item_code(n) turns a number from item_code_seq into a code:
--   a letter, then 5 letters/digits from Crockford's base 32 (no I, L, O,
--   U, so nothing reads like 1 or 0). The numbers are scrambled with a
--   one-to-one mapping, so codes never repeat and do not look sequential.
--   Starting with a letter keeps them apart from every old numeric barcode
--   and stops Excel from reading them as numbers. 738,197,504 codes.
-- * A product takes the code of its first colour/size, so a product with a
--   single colour/size has one code everywhere. The app (ItemCodes) uses
--   the same function for new items.
-- * Every old code/barcode (PRD-XXXX, PRD-XXXX-1, 100001, the 12-digit
--   barcodes) stays scannable through item_code_aliases, so labels and
--   count sheets already printed keep working.
-- Idempotent: rows already in the new format are left alone.
-- =============================================================

CREATE SEQUENCE IF NOT EXISTS item_code_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE FUNCTION elramy_item_code(n BIGINT) RETURNS VARCHAR AS $$
DECLARE
    letters CONSTANT TEXT   := 'ABCDEFGHJKMNPQRSTVWXYZ';            -- 22
    digits  CONSTANT TEXT   := '0123456789ABCDEFGHJKMNPQRSTVWXYZ';  -- 32
    space   CONSTANT BIGINT := 738197504;                           -- 22 * 32^5
    idx  BIGINT;
    rest BIGINT;
    code TEXT := '';
BEGIN
    IF n < 1 OR n > space THEN
        RAISE EXCEPTION 'item code number % out of range', n;
    END IF;
    -- 387420489 is coprime with 22 * 2^25, so this is a bijection on [0, space).
    idx  := (n * 387420489 + 123456789) % space;
    rest := idx % 33554432;                                         -- 32^5
    FOR i IN 1..5 LOOP
        code := substr(digits, (rest % 32)::INT + 1, 1) || code;
        rest := rest / 32;
    END LOOP;
    RETURN substr(letters, (idx / 33554432)::INT + 1, 1) || code;
END
$$ LANGUAGE plpgsql IMMUTABLE STRICT;

CREATE TABLE IF NOT EXISTS item_code_aliases (
    code       VARCHAR(100) PRIMARY KEY,
    variant_id BIGINT REFERENCES product_variants (id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products (id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_item_code_aliases_one_target CHECK ((variant_id IS NULL) <> (product_id IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_item_code_aliases_variant ON item_code_aliases (variant_id);
CREATE INDEX IF NOT EXISTS idx_item_code_aliases_product ON item_code_aliases (product_id);

DO $$
DECLARE
    p RECORD;
    v RECORD;
    new_code TEXT;
    first_variant BOOLEAN;
BEGIN
    FOR p IN SELECT id, code FROM products ORDER BY id LOOP
        IF p.code ~ '^[A-Z][0-9A-Z]{5}$' THEN
            CONTINUE;                                               -- already recoded
        END IF;
        INSERT INTO item_code_aliases (code, product_id) VALUES (p.code, p.id) ON CONFLICT DO NOTHING;
        new_code := elramy_item_code(nextval('item_code_seq'));
        UPDATE products SET code = new_code WHERE id = p.id;

        first_variant := TRUE;
        FOR v IN SELECT id, code, barcode, legacy_barcode FROM product_variants WHERE product_id = p.id ORDER BY id LOOP
            INSERT INTO item_code_aliases (code, variant_id)
            SELECT c, v.id FROM unnest(ARRAY[v.code, v.barcode, v.legacy_barcode]) AS c
            WHERE c IS NOT NULL AND c <> ''
            ON CONFLICT DO NOTHING;
            IF NOT first_variant THEN
                new_code := elramy_item_code(nextval('item_code_seq'));
            END IF;
            UPDATE product_variants SET code = new_code, barcode = new_code WHERE id = v.id;
            first_variant := FALSE;
        END LOOP;
    END LOOP;
END $$;
