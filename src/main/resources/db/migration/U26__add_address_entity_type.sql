DROP INDEX idx_address_owner;

ALTER TABLE address DROP COLUMN owner_type;

ALTER TABLE address RENAME COLUMN owner_id TO user_id;

CREATE INDEX idx_address_user_id
    ON address (user_id);

ALTER TABLE address
    ADD CONSTRAINT fk_address_user
        FOREIGN KEY (user_id)
            REFERENCES users (user_id)
                ON DELETE CASCADE;