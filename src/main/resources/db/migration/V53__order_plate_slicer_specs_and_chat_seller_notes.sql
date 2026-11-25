-- ============================================================================
-- V53: Metadados completos do fatiador por mesa (order_file_asset) e
--      anotações privadas do vendedor por negociação (chat.seller_notes)
-- ============================================================================

ALTER TABLE order_file_asset
    ADD COLUMN IF NOT EXISTS quantity                     INTEGER        NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS wall_count                   INTEGER,
    ADD COLUMN IF NOT EXISTS infill_pattern               VARCHAR(40),
    ADD COLUMN IF NOT EXISTS adhesion_type                VARCHAR(40),
    ADD COLUMN IF NOT EXISTS support_type                 VARCHAR(40),
    ADD COLUMN IF NOT EXISTS finish                       VARCHAR(40),
    ADD COLUMN IF NOT EXISTS tolerance                    VARCHAR(40),
    ADD COLUMN IF NOT EXISTS plate_notes                  TEXT,
    ADD COLUMN IF NOT EXISTS colors_json                  JSONB,
    ADD COLUMN IF NOT EXISTS estimated_weight_grams       NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS estimated_print_time_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS estimated_price_brl          NUMERIC(10, 2);

ALTER TABLE chat
    ADD COLUMN IF NOT EXISTS seller_notes TEXT;
