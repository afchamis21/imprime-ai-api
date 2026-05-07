ALTER TABLE users
    DROP CONSTRAINT fk_users_primary_address;

DROP TABLE address CASCADE CONSTRAINTS;