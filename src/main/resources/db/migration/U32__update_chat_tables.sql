-- =============================================
-- CHAT
-- =============================================

ALTER TABLE chat
    DROP CONSTRAINT IF EXISTS fk_chat_company_guid;

ALTER TABLE chat
    DROP CONSTRAINT IF EXISTS fk_chat_user_guid;

ALTER TABLE chat
    DROP CONSTRAINT IF EXISTS fk_chat_order;

ALTER TABLE chat
    DROP COLUMN IF EXISTS company_guid;

ALTER TABLE chat
    DROP COLUMN IF EXISTS user_guid;

ALTER TABLE chat
    RENAME COLUMN order_guid TO order_id;

-- =============================================
-- CHAT MEMBER
-- =============================================

CREATE TABLE chat_member
(
    chat_id     UUID                     NOT NULL,
    user_id     VARCHAR(36)              NOT NULL,

    -- Auditable
    guid        VARCHAR(36) UNIQUE       NOT NULL DEFAULT gen_random_uuid(),
    status      VARCHAR(5),
    create_dt   TIMESTAMP WITH TIME ZONE NOT NULL,
    create_user VARCHAR(100),
    update_dt   TIMESTAMP WITH TIME ZONE,
    update_user VARCHAR(100),

    CONSTRAINT pk_chat_member
        PRIMARY KEY (chat_id, user_id),

    CONSTRAINT fk_chat_member_chat
        FOREIGN KEY (chat_id)
            REFERENCES chat (chat_id),

    CONSTRAINT fk_chat_member_user
        FOREIGN KEY (user_id)
            REFERENCES users (guid)
);


-- =============================================
-- INDEXES
-- =============================================

CREATE INDEX idx_chat_member_user_id
    ON chat_member (user_id);