ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(1000);

COMMENT ON COLUMN users.avatar_url IS 'Public URL of the user profile avatar image';
