DROP INDEX IF EXISTS idx_address_latitude_longitude;

ALTER TABLE address
    DROP CONSTRAINT IF EXISTS chk_address_latitude,
    DROP CONSTRAINT IF EXISTS chk_address_longitude,
    DROP COLUMN IF EXISTS latitude,
    DROP COLUMN IF EXISTS longitude;