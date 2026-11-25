-- ============================================================================
-- U53: Rollback da V53 (Metadados do fatiador por mesa e chat.seller_notes)
-- ============================================================================

ALTER TABLE chat
    DROP COLUMN IF EXISTS seller_notes;

ALTER TABLE order_file_asset
    DROP COLUMN IF EXISTS estimated_price_brl,
    DROP COLUMN IF EXISTS estimated_print_time_minutes,
    DROP COLUMN IF EXISTS estimated_weight_grams,
    DROP COLUMN IF EXISTS colors_json,
    DROP COLUMN IF EXISTS plate_notes,
    DROP COLUMN IF EXISTS tolerance,
    DROP COLUMN IF EXISTS finish,
    DROP COLUMN IF EXISTS support_type,
    DROP COLUMN IF EXISTS adhesion_type,
    DROP COLUMN IF EXISTS infill_pattern,
    DROP COLUMN IF EXISTS wall_count,
    DROP COLUMN IF EXISTS quantity;
