DROP INDEX IF EXISTS idx_print_queue_order;
DELETE FROM print_queue_item WHERE asset_id IS NULL;
ALTER TABLE print_queue_item
    ALTER COLUMN asset_id SET NOT NULL;
