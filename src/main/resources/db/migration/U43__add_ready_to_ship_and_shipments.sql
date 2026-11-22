DROP INDEX IF EXISTS idx_order_shipment_tracking;
DROP INDEX IF EXISTS idx_order_shipment_order_status;
DROP INDEX IF EXISTS idx_order_shipping_quote_owner;
DROP TABLE IF EXISTS order_shipping_quote;
DROP TABLE IF EXISTS melhor_envio_credential;
DROP TABLE IF EXISTS order_shipment;

ALTER TABLE orders
    DROP COLUMN IF EXISTS tracking_url,
    DROP COLUMN IF EXISTS tracking_status,
    DROP COLUMN IF EXISTS carrier_name,
    DROP COLUMN IF EXISTS external_shipment_id,
    DROP COLUMN IF EXISTS shipping_quote_snapshot,
    DROP COLUMN IF EXISTS shipping_delivery_days,
    DROP COLUMN IF EXISTS shipping_quote_price,
    DROP COLUMN IF EXISTS shipping_service_name,
    DROP COLUMN IF EXISTS shipping_service_id;

DELETE FROM order_status_lkup WHERE code = '2.015';
