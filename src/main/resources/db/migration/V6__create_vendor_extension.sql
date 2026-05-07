CREATE TABLE vendor_extension
(
    user_id     NUMBER PRIMARY KEY,
    cnpj        VARCHAR2(20) UNIQUE,
    address_id  NUMBER,
    status      VARCHAR2(5),
    create_dt   TIMESTAMP WITH TIME ZONE
        DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_user VARCHAR2(100),
    update_dt   TIMESTAMP WITH TIME ZONE,
    update_user VARCHAR2(100),

    CONSTRAINT fk_vendor_user
        FOREIGN KEY (user_id)
            REFERENCES users (user_id)
                ON DELETE CASCADE,

    CONSTRAINT fk_vendor_address
        FOREIGN KEY (address_id)
            REFERENCES address (address_id)
);