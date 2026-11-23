DROP INDEX IF EXISTS uq_review_active_author_order_target;
DROP INDEX IF EXISTS uq_review_active_author_target_no_order;

CREATE UNIQUE INDEX IF NOT EXISTS uq_review_active_author_target
    ON review (author_user_id, target_type, target_id)
    WHERE status = 'A';

DELETE FROM message_lkup
WHERE code = 'REV_003';
