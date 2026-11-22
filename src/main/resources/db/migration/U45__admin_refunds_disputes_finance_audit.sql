DROP INDEX IF EXISTS idx_admin_audit_target;
DROP INDEX IF EXISTS idx_admin_audit_action_dt;
DROP INDEX IF EXISTS idx_admin_audit_admin_dt;
DROP TABLE IF EXISTS admin_audit_log;

DROP INDEX IF EXISTS idx_designer_fin_tx_type_status;
DROP INDEX IF EXISTS idx_designer_fin_tx_user_status;
DROP TABLE IF EXISTS designer_finance_transaction;

ALTER TABLE maker_finance_transaction
    DROP COLUMN IF EXISTS failure_reason,
    DROP COLUMN IF EXISTS processed_at,
    DROP COLUMN IF EXISTS processed_by_user_id;

ALTER TABLE support_ticket_message
    DROP COLUMN IF EXISTS attachments;

DROP INDEX IF EXISTS idx_support_ticket_dispute_status;
ALTER TABLE support_ticket
    DROP COLUMN IF EXISTS resolved_at,
    DROP COLUMN IF EXISTS resolution_notes,
    DROP COLUMN IF EXISTS assigned_admin_id,
    DROP COLUMN IF EXISTS priority,
    DROP COLUMN IF EXISTS is_dispute;

DROP INDEX IF EXISTS idx_refund_request_status;
DROP INDEX IF EXISTS idx_refund_request_order;
DROP TABLE IF EXISTS refund_request;
