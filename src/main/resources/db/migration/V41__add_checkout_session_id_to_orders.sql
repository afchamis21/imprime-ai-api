ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS checkout_session_id VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_orders_checkout_session_id
    ON orders (checkout_session_id);
