ALTER TABLE users
    DROP CONSTRAINT idx_user_guid;

ALTER TABLE users DROP COLUMN guid;
ALTER TABLE address DROP COLUMN guid;
ALTER TABLE file_asset DROP COLUMN guid;
ALTER TABLE asset DROP COLUMN guid;
ALTER TABLE filament DROP COLUMN guid;
ALTER TABLE company DROP COLUMN guid;
ALTER TABLE pricing_strategy DROP COLUMN guid;
ALTER TABLE pricing_rule DROP COLUMN guid;
ALTER TABLE order_status_lkup DROP COLUMN guid;
ALTER TABLE orders DROP COLUMN guid;
ALTER TABLE order_status DROP COLUMN guid;
ALTER TABLE auth_token DROP COLUMN guid;