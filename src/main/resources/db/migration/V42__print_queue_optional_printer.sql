ALTER TABLE print_queue_item
    ALTER COLUMN asset_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_print_queue_order
    ON print_queue_item (order_id);
