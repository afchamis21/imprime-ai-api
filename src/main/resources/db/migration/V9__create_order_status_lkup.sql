CREATE TABLE order_status_lkup
(
    code        VARCHAR(30)                        NOT NULL PRIMARY KEY,
    name        VARCHAR(30)                        NOT NULL,
    description VARCHAR(255),
    status      VARCHAR(5),
    create_dt   TIMESTAMP WITH TIME ZONE
                          DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_user VARCHAR(100),
    update_dt   TIMESTAMP WITH TIME ZONE,
    update_user VARCHAR(100)
);