-- =============================================
-- CHAT
-- =============================================
CREATE TABLE chat
(
    chat_id     UUID PRIMARY KEY,
    order_id    BIGINT,

    -- Auditable
    guid        VARCHAR(36) UNIQUE       NOT NULL DEFAULT gen_random_uuid(),
    status      VARCHAR(5),
    create_dt   TIMESTAMP WITH TIME ZONE NOT NULL,
    create_user VARCHAR(100),
    update_dt   TIMESTAMP WITH TIME ZONE,
    update_user VARCHAR(100)
);

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
-- CHAT MESSAGE
-- =============================================
CREATE TABLE chat_message
(
    id          UUID PRIMARY KEY,
    chat_id     UUID                     NOT NULL,
    sender_id   VARCHAR(36)              NOT NULL,
    content     TEXT,
    type        VARCHAR(50),

    -- Auditable
    guid        VARCHAR(36) UNIQUE       NOT NULL DEFAULT gen_random_uuid(),
    status      VARCHAR(5),
    create_dt   TIMESTAMP WITH TIME ZONE NOT NULL,
    create_user VARCHAR(100),
    update_dt   TIMESTAMP WITH TIME ZONE,
    update_user VARCHAR(100),

    CONSTRAINT fk_chat_message_chat
        FOREIGN KEY (chat_id)
            REFERENCES chat (chat_id),

    CONSTRAINT fk_chat_message_sender
        FOREIGN KEY (sender_id)
            REFERENCES users (guid)
);

-- =============================================
-- INDEXES
-- =============================================
CREATE INDEX idx_chat_order_id
    ON chat (order_id);

CREATE INDEX idx_chat_member_user_id
    ON chat_member (user_id);

CREATE INDEX idx_chat_message_chat_created
    ON chat_message (chat_id, create_dt DESC);

CREATE INDEX idx_chat_message_sender_id
    ON chat_message (sender_id);
