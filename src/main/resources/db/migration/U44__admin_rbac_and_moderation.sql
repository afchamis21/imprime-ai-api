DROP INDEX IF EXISTS idx_company_approval_status;
DROP INDEX IF EXISTS idx_model_3d_moderation_status;
DROP INDEX IF EXISTS idx_users_role_status;

ALTER TABLE company
    DROP COLUMN IF EXISTS reviewed_at,
    DROP COLUMN IF EXISTS reviewed_by_user_id,
    DROP COLUMN IF EXISTS approval_reason,
    DROP COLUMN IF EXISTS approval_status;

ALTER TABLE model_3d
    DROP COLUMN IF EXISTS moderated_at,
    DROP COLUMN IF EXISTS moderated_by_user_id,
    DROP COLUMN IF EXISTS dmca_reference,
    DROP COLUMN IF EXISTS dmca_claimant,
    DROP COLUMN IF EXISTS moderation_reason,
    DROP COLUMN IF EXISTS moderation_status;

ALTER TABLE users
    DROP COLUMN IF EXISTS suspended_at,
    DROP COLUMN IF EXISTS suspension_reason,
    DROP COLUMN IF EXISTS role;
