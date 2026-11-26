CREATE TABLE ip_notice_moderation_action
(
    content_report_id BIGINT NOT NULL REFERENCES content_report (content_report_id),
    action VARCHAR(20) NOT NULL CHECK (action IN ('SUSPEND_OWNER', 'TAKE_DOWN')),
    admin_user_id BIGINT NOT NULL REFERENCES users (user_id),
    reason VARCHAR(400) NOT NULL,
    acted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (content_report_id, action)
);
