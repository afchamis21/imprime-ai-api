-- U72: Rollback V72 marketplace instant quoting and speed tiers

DELETE FROM message_lkup
WHERE code IN ('SLC_001', 'SLC_002', 'SLC_003', 'SLC_004', 'SLC_005', 'SLC_006');

ALTER TABLE order_file_asset
    DROP COLUMN IF EXISTS validated_gcode_file_asset_id,
    DROP COLUMN IF EXISTS validated_3mf_file_asset_id,
    DROP COLUMN IF EXISTS slicing_validation_error,
    DROP COLUMN IF EXISTS slicing_validated_at,
    DROP COLUMN IF EXISTS slicing_validation_status,
    DROP COLUMN IF EXISTS time_divergence_percent,
    DROP COLUMN IF EXISTS weight_divergence_percent,
    DROP COLUMN IF EXISTS server_base_print_time_seconds,
    DROP COLUMN IF EXISTS server_total_weight_grams,
    DROP COLUMN IF EXISTS server_support_weight_grams,
    DROP COLUMN IF EXISTS server_model_weight_grams,
    DROP COLUMN IF EXISTS client_base_print_time_seconds,
    DROP COLUMN IF EXISTS client_support_weight_grams,
    DROP COLUMN IF EXISTS client_model_weight_grams,
    DROP COLUMN IF EXISTS slicer_profile_id,
    DROP COLUMN IF EXISTS slicer_engine_id;

DROP INDEX IF EXISTS idx_orders_slicing_validation_status;

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS chk_orders_selected_speed_tier,
    DROP CONSTRAINT IF EXISTS chk_orders_slicing_validation_status,
    DROP CONSTRAINT IF EXISTS chk_orders_pricing_source,
    DROP COLUMN IF EXISTS slicing_validation_summary,
    DROP COLUMN IF EXISTS slicing_validated_at,
    DROP COLUMN IF EXISTS slicing_validation_status,
    DROP COLUMN IF EXISTS server_validated_amount,
    DROP COLUMN IF EXISTS client_quoted_amount,
    DROP COLUMN IF EXISTS estimated_print_time_seconds,
    DROP COLUMN IF EXISTS selected_speed_multiplier,
    DROP COLUMN IF EXISTS selected_speed_tier,
    DROP COLUMN IF EXISTS selected_printer_name,
    DROP COLUMN IF EXISTS selected_printer_asset_id,
    DROP COLUMN IF EXISTS pricing_source;

DROP INDEX IF EXISTS idx_maker_material_price_lookup;
DROP INDEX IF EXISTS uq_maker_material_price_active_company_mat;
DROP TABLE IF EXISTS maker_material_price;

DROP INDEX IF EXISTS uq_maker_pricing_profile_active_company;
DROP TABLE IF EXISTS maker_pricing_profile;

ALTER TABLE asset
    DROP CONSTRAINT IF EXISTS chk_asset_speed_multiplier_override,
    DROP CONSTRAINT IF EXISTS chk_asset_speed_tier,
    DROP COLUMN IF EXISTS speed_multiplier_override,
    DROP COLUMN IF EXISTS speed_tier;

DELETE FROM printer_catalog_material
WHERE printer_catalog_id IN (
    SELECT printer_catalog_id
    FROM printer_catalog
    WHERE slug IN ('creality-ender-3-v2', 'prusa-mk3s-plus', 'artillery-sidewinder-x2')
);

DELETE FROM printer_catalog
WHERE slug IN ('creality-ender-3-v2', 'prusa-mk3s-plus', 'artillery-sidewinder-x2');

ALTER TABLE printer_catalog
    DROP CONSTRAINT IF EXISTS chk_pc_speed_multiplier_positive,
    DROP CONSTRAINT IF EXISTS chk_pc_speed_tier,
    DROP COLUMN IF EXISTS speed_multiplier,
    DROP COLUMN IF EXISTS speed_tier;
