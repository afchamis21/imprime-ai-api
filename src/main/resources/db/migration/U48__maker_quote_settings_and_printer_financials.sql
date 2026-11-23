DROP INDEX IF EXISTS uq_maker_quote_settings_active_company;
DROP TABLE IF EXISTS maker_quote_settings;

ALTER TABLE asset
    DROP COLUMN IF EXISTS hourly_depreciation_rate,
    DROP COLUMN IF EXISTS lifespan_hours,
    DROP COLUMN IF EXISTS purchase_price;
