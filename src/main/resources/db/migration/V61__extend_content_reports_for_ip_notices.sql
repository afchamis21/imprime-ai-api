ALTER TABLE content_report
    ALTER COLUMN reporter_user_id DROP NOT NULL,
    ADD COLUMN claimant_name VARCHAR(150),
    ADD COLUMN claimant_email VARCHAR(254),
    ADD CONSTRAINT content_report_reporter_required
        CHECK (reporter_user_id IS NOT NULL OR (claimant_name IS NOT NULL AND claimant_email IS NOT NULL));
