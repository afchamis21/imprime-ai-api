CREATE TABLE order_status_lkup
(
    code        VARCHAR2(30)                        NOT NULL PRIMARY KEY,
    name        VARCHAR2(30)                        NOT NULL,
    description VARCHAR2(255),
    status      VARCHAR2(5),
    create_dt   TIMESTAMP WITH TIME ZONE
                          DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_user VARCHAR2(100),
    update_dt   TIMESTAMP WITH TIME ZONE,
    update_user VARCHAR2(100)
);