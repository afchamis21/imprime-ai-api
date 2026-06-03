ALTER TABLE users
    DROP CONSTRAINT fk_users_company_id;

DROP INDEX idx_user_company_id;

ALTER TABLE users
    DROP COLUMN company_id;

DROP TABLE company CASCADE CONSTRAINTS;