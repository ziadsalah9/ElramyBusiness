-- Empties the store for a fresh start. Kept: the admin1 account, branches,
-- store settings, product types and the migration history. Everything else
-- (products, drafts, stock, invoices, returns, customers, suppliers, payments,
-- installments, expenses, cash closings, stocktakes, activity log, other users)
-- is removed and numbering starts again from 1.
-- Always take a backup first (the Mac and Windows reset scripts do).
DO $$
DECLARE
    keep CONSTANT text[] := ARRAY['users', 'branches', 'store_settings', 'product_types', 'flyway_schema_history'];
    tables text;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin1') THEN
        RAISE EXCEPTION 'admin1 not found - nothing was changed';
    END IF;
    SELECT string_agg(format('%I.%I', schemaname, tablename), ', ')
      INTO tables
      FROM pg_tables
     WHERE schemaname = 'public' AND tablename <> ALL (keep);
    EXECUTE 'TRUNCATE TABLE ' || tables || ' RESTART IDENTITY';
    DELETE FROM users WHERE username <> 'admin1';
END $$;
