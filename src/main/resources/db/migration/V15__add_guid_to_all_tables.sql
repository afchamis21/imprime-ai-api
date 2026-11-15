ALTER TABLE users
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE address
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE file_asset
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE asset
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE filament
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE company
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE pricing_strategy
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE pricing_rule
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE order_status_lkup
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE orders
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE order_status
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE auth_token
    ADD guid VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid();

CREATE INDEX idx_user_guid
    ON users (guid);