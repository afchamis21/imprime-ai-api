-- ============================================================================
-- U51: Rollback da V51 (Catálogos de Impressoras/Filamentos e Extensões Técnicas)
-- ============================================================================

ALTER TABLE order_file_asset
    DROP COLUMN IF EXISTS urgency,
    DROP COLUMN IF EXISTS layer_height_mm,
    DROP COLUMN IF EXISTS color_count,
    DROP COLUMN IF EXISTS dimension_z_mm,
    DROP COLUMN IF EXISTS dimension_y_mm,
    DROP COLUMN IF EXISTS dimension_x_mm;

DROP INDEX IF EXISTS idx_filament_catalog_id;
ALTER TABLE filament
    DROP COLUMN IF EXISTS filament_catalog_id;

DROP INDEX IF EXISTS idx_asset_printer_catalog_id;
ALTER TABLE asset
    DROP COLUMN IF EXISTS supported_materials_override,
    DROP COLUMN IF EXISTS is_enclosed_override,
    DROP COLUMN IF EXISTS multicolor_slots,
    DROP COLUMN IF EXISTS supports_multicolor,
    DROP COLUMN IF EXISTS printer_catalog_id;

DROP INDEX IF EXISTS idx_company_primary_printer_catalog_id;
ALTER TABLE company
    DROP COLUMN IF EXISTS technical_notes,
    DROP COLUMN IF EXISTS min_layer_height_mm,
    DROP COLUMN IF EXISTS supports_express_orders,
    DROP COLUMN IF EXISTS offers_cad_modeling,
    DROP COLUMN IF EXISTS offers_painting,
    DROP COLUMN IF EXISTS offers_post_processing,
    DROP COLUMN IF EXISTS max_multicolor_slots,
    DROP COLUMN IF EXISTS supports_multicolor,
    DROP COLUMN IF EXISTS primary_printer_catalog_id;

DROP INDEX IF EXISTS idx_filament_catalog_color_hex;
DROP INDEX IF EXISTS idx_filament_catalog_brand;
DROP INDEX IF EXISTS idx_filament_catalog_status_material;
DROP TABLE IF EXISTS filament_catalog;

DROP INDEX IF EXISTS idx_printer_catalog_material_mat;
DROP TABLE IF EXISTS printer_catalog_material;

DROP INDEX IF EXISTS idx_printer_catalog_flags;
DROP INDEX IF EXISTS idx_printer_catalog_status_brand;
DROP INDEX IF EXISTS uq_printer_catalog_brand_model_active;
DROP TABLE IF EXISTS printer_catalog;
