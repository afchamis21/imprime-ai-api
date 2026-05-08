ALTER TABLE users ADD guid VARCHAR2(36);
ALTER TABLE address ADD guid VARCHAR2(36);
ALTER TABLE file_asset ADD guid VARCHAR2(36);
ALTER TABLE asset ADD guid VARCHAR2(36);
ALTER TABLE filament ADD guid VARCHAR2(36);
ALTER TABLE company ADD guid VARCHAR2(36);
ALTER TABLE pricing_strategy ADD guid VARCHAR2(36);
ALTER TABLE pricing_rule ADD guid VARCHAR2(36);
ALTER TABLE order_status_lkup ADD guid VARCHAR2(36);
ALTER TABLE orders ADD guid VARCHAR2(36);
ALTER TABLE order_status ADD guid VARCHAR2(36);
ALTER TABLE auth_token ADD guid VARCHAR2(36);

CREATE INDEX idx_user_guid
    ON users (guid);