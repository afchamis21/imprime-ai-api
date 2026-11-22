DROP INDEX IF EXISTS idx_orders_checkout_session_id;
ALTER TABLE orders
    DROP COLUMN IF EXISTS checkout_session_id;
