-- =============================================================
-- V7: single-branch mode - move all stock into فرع كوم حماة.
-- -------------------------------------------------------------
-- The store runs one shop. Every other ACTIVE branch's stock is
-- moved into the main branch as a regular branch transfer (transfer
-- + items + TRANSFER_OUT/IN ledger rows, same shape as
-- BranchTransferServiceImpl), then that branch is deactivated.
-- Its invoices and cash closings are left untouched as history.
--
-- The main branch is found BY NAME, so on a database without it
-- this migration does nothing. Re-running is a no-op because the
-- other branches end up inactive.
-- =============================================================

DO $$
DECLARE
    main_id     BIGINT;
    main_name   TEXT;
    other       RECORD;
    src         RECORD;
    transfer_id BIGINT;
    dest_id     BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'public' AND table_name = 'branches') THEN
        RETURN;
    END IF;

    SELECT id, name INTO main_id, main_name
      FROM branches
     WHERE btrim(name) = 'فرع كوم حماة'
     ORDER BY id
     LIMIT 1;

    IF main_id IS NULL THEN
        RAISE NOTICE 'V7: main branch not found, nothing to consolidate.';
        RETURN;
    END IF;

    UPDATE branches SET active = TRUE WHERE id = main_id AND active IS NOT TRUE;

    FOR other IN
        SELECT id, name FROM branches WHERE id <> main_id AND active IS NOT FALSE ORDER BY id
    LOOP
        IF EXISTS (SELECT 1 FROM product_stocks WHERE branch_id = other.id AND quantity > 0) THEN

            INSERT INTO branch_transfers (from_branch_id, to_branch_id, notes, created_at, updated_at)
            VALUES (other.id, main_id, 'دمج المخزون عند التحويل إلى فرع واحد', now(), now())
            RETURNING id INTO transfer_id;

            FOR src IN
                SELECT ps.id, ps.product_id, ps.quantity, COALESCE(p.minimum_quantity, 0) AS min_qty
                  FROM product_stocks ps
                  JOIN products p ON p.id = ps.product_id
                 WHERE ps.branch_id = other.id AND ps.quantity > 0
                 ORDER BY ps.id
                   FOR UPDATE OF ps
            LOOP
                SELECT id INTO dest_id
                  FROM product_stocks
                 WHERE product_id = src.product_id AND branch_id = main_id
                   FOR UPDATE;

                IF dest_id IS NULL THEN
                    INSERT INTO product_stocks (product_id, branch_id, quantity, minimum_quantity, created_at, updated_at)
                    VALUES (src.product_id, main_id, 0, src.min_qty, now(), now())
                    RETURNING id INTO dest_id;
                END IF;

                UPDATE product_stocks SET quantity = quantity + src.quantity, updated_at = now() WHERE id = dest_id;
                UPDATE product_stocks SET quantity = 0, updated_at = now() WHERE id = src.id;

                INSERT INTO branch_transfer_items (transfer_id, product_stock_id, quantity, created_at, updated_at)
                VALUES (transfer_id, src.id, src.quantity, now(), now());

                INSERT INTO stock_transactions
                    (product_stock_id, transaction_type, quantity, reference_id, reference_type, notes, created_at, updated_at)
                VALUES
                    (src.id,  'TRANSFER_OUT', src.quantity, transfer_id, 'BRANCH_TRANSFER', 'Transfer to branch ' || main_name, now(), now()),
                    (dest_id, 'TRANSFER_IN',  src.quantity, transfer_id, 'BRANCH_TRANSFER', 'Transfer from branch ' || other.name, now(), now());

                dest_id := NULL;
            END LOOP;
        END IF;

        UPDATE branches SET active = FALSE, updated_at = now() WHERE id = other.id;
        RAISE NOTICE 'V7: consolidated branch % (%) into %.', other.id, other.name, main_name;
    END LOOP;
END $$;
