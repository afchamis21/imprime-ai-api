DROP INDEX IF EXISTS idx_order_shipping_quote_chat_message;

ALTER TABLE order_shipping_quote
    DROP COLUMN IF EXISTS chat_message_guid;

ALTER TABLE orders
    DROP COLUMN IF EXISTS package_length,
    DROP COLUMN IF EXISTS package_width,
    DROP COLUMN IF EXISTS package_height,
    DROP COLUMN IF EXISTS package_weight,
    DROP COLUMN IF EXISTS production_days,
    DROP COLUMN IF EXISTS service_amount;

ALTER TABLE chat_message
    DROP COLUMN IF EXISTS package_length,
    DROP COLUMN IF EXISTS package_width,
    DROP COLUMN IF EXISTS package_height,
    DROP COLUMN IF EXISTS package_weight,
    DROP COLUMN IF EXISTS production_days;
