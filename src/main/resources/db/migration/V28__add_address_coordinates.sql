ALTER TABLE address
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION;

ALTER TABLE address
    ADD CONSTRAINT chk_address_latitude
        CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    ADD CONSTRAINT chk_address_longitude
        CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180));

CREATE INDEX idx_address_latitude_longitude
    ON address (latitude, longitude);

COMMENT ON COLUMN address.latitude IS 'Latitude in decimal degrees (WGS84)';
COMMENT ON COLUMN address.longitude IS 'Longitude in decimal degrees (WGS84)';
