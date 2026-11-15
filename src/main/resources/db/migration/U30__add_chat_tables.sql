-- =============================================
-- DROP INDEXES
-- =============================================
DROP INDEX IF EXISTS idx_chat_message_sender_id;
DROP INDEX IF EXISTS idx_chat_message_chat_created;
DROP INDEX IF EXISTS idx_chat_member_user_id;
DROP INDEX IF EXISTS idx_chat_order_id;

-- =============================================
-- DROP TABLES
-- =============================================
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_member;
DROP TABLE IF EXISTS chat;