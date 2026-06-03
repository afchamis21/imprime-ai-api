ALTER TABLE address DROP CONSTRAINT fk_address_user;

DROP INDEX idx_address_user_id;

ALTER TABLE address RENAME COLUMN user_id TO owner_id;
ALTER TABLE address ADD owner_type VARCHAR2(5) NOT NULL;

CREATE INDEX idx_address_owner
    ON address (owner_type, owner_id);