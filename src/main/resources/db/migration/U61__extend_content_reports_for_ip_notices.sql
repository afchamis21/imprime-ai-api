-- PostgreSQL refuses this rollback while external notices still exist.
ALTER TABLE content_report
    DROP CONSTRAINT content_report_reporter_required,
    ALTER COLUMN reporter_user_id SET NOT NULL,
    DROP COLUMN claimant_name,
    DROP COLUMN claimant_email;
