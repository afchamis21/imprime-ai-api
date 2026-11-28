ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS production_days INTEGER,
    ADD COLUMN IF NOT EXISTS package_weight  NUMERIC(10, 3),
    ADD COLUMN IF NOT EXISTS package_height  NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS package_width   NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS package_length  NUMERIC(10, 2);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS service_amount  NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS production_days INTEGER,
    ADD COLUMN IF NOT EXISTS package_weight  NUMERIC(10, 3),
    ADD COLUMN IF NOT EXISTS package_height  NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS package_width   NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS package_length  NUMERIC(10, 2);

ALTER TABLE order_shipping_quote
    ADD COLUMN IF NOT EXISTS chat_message_guid VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_order_shipping_quote_chat_message
    ON order_shipping_quote (chat_message_guid)
    WHERE chat_message_guid IS NOT NULL;
