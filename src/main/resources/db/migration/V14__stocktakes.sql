-- =============================================================
-- V14: stocktaking (الجرد).
-- -------------------------------------------------------------
-- A stocktake counts the whole store or some product types. Each
-- counted colour/size is one line holding the counted quantity and the
-- system quantity at the moment it was counted; on approval the
-- difference (counted - that snapshot) is applied to the current stock
-- through the stock ledger, so sales made during the count are kept.
-- Only one stocktake can be open at a time. Additive only.
-- =============================================================

CREATE TABLE IF NOT EXISTS stocktakes (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(30)  NOT NULL,
    title         VARCHAR(200) NOT NULL,
    scope_types   TEXT,
    status        VARCHAR(20)  NOT NULL,
    notes         VARCHAR(500),
    created_by    BIGINT REFERENCES users (id),
    approved_by   BIGINT REFERENCES users (id),
    approved_at   TIMESTAMP,
    shortage_cost NUMERIC(18, 2),
    surplus_cost  NUMERIC(18, 2),
    shortage_sell NUMERIC(18, 2),
    surplus_sell  NUMERIC(18, 2),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    CONSTRAINT uq_stocktakes_code UNIQUE (code),
    CONSTRAINT chk_stocktakes_status CHECK (status IN ('OPEN', 'APPROVED', 'CANCELLED'))
);

-- At most one OPEN stocktake.
CREATE UNIQUE INDEX IF NOT EXISTS uq_stocktakes_one_open ON stocktakes (status) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_stocktakes_created ON stocktakes (created_at DESC);

CREATE TABLE IF NOT EXISTS stocktake_lines (
    id                  BIGSERIAL PRIMARY KEY,
    stocktake_id        BIGINT  NOT NULL REFERENCES stocktakes (id) ON DELETE CASCADE,
    variant_id          BIGINT  NOT NULL REFERENCES product_variants (id),
    product_id          BIGINT  NOT NULL REFERENCES products (id),
    counted_qty         INTEGER NOT NULL,
    system_qty_at_count INTEGER NOT NULL,
    counted_at          TIMESTAMP,
    counted_by          BIGINT REFERENCES users (id),
    reason              VARCHAR(30),
    note                VARCHAR(500),
    unit_cost           NUMERIC(18, 2),
    unit_price          NUMERIC(18, 2),
    applied_variance    INTEGER,
    clamped             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    CONSTRAINT uq_stocktake_lines_variant UNIQUE (stocktake_id, variant_id),
    CONSTRAINT chk_stocktake_lines_counted CHECK (counted_qty >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stocktake_lines_stocktake ON stocktake_lines (stocktake_id);
