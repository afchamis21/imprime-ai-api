ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'BUYER',
    ADD COLUMN IF NOT EXISTS suspension_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP WITH TIME ZONE;

UPDATE users
SET role = 'MAKER'
WHERE company_id IS NOT NULL;

UPDATE users u
SET role = 'DESIGNER'
WHERE u.company_id IS NULL
  AND u.role = 'BUYER'
  AND EXISTS (
      SELECT 1
      FROM model_3d m
      WHERE m.author_user_id = u.user_id
  );

CREATE INDEX IF NOT EXISTS idx_users_role_status
    ON users (role, status);

ALTER TABLE model_3d
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS moderation_reason TEXT,
    ADD COLUMN IF NOT EXISTS dmca_claimant VARCHAR(200),
    ADD COLUMN IF NOT EXISTS dmca_reference VARCHAR(120),
    ADD COLUMN IF NOT EXISTS moderated_by_user_id BIGINT REFERENCES users (user_id),
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_model_3d_moderation_status
    ON model_3d (moderation_status, status);

ALTER TABLE company
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS approval_reason TEXT,
    ADD COLUMN IF NOT EXISTS reviewed_by_user_id BIGINT REFERENCES users (user_id),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_company_approval_status
    ON company (approval_status, status);
