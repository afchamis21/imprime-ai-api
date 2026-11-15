-- =============================================
-- CHAT MEMBER
-- =============================================

DROP INDEX IF EXISTS idx_chat_member_user_id;
DROP TABLE IF EXISTS chat_member;


-- =============================================
-- CHAT
-- =============================================

ALTER TABLE chat
    DROP COLUMN order_id;

ALTER TABLE chat
    ADD column order_guid VARCHAR(36);

ALTER TABLE chat
    ADD COLUMN user_guid VARCHAR(36);

ALTER TABLE chat
    ADD COLUMN company_guid VARCHAR(36);

ALTER TABLE chat
    ADD CONSTRAINT fk_chat_user_guid
        FOREIGN KEY (user_guid)
            REFERENCES users (guid);

ALTER TABLE chat
    ADD CONSTRAINT fk_chat_company_guid
        FOREIGN KEY (company_guid)
            REFERENCES company (guid);

ALTER TABLE chat
    ADD CONSTRAINT fk_chat_order
        FOREIGN KEY (order_guid)
            REFERENCES orders (guid);