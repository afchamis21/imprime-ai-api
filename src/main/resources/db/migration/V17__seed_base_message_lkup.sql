INSERT INTO message_lkup (code, text, type, guid, status, create_dt, create_user, update_dt, update_user)
VALUES ('SYS_500', 'Internal Server Error', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre');

INSERT INTO message_lkup (code, text, type, guid, status, create_dt, create_user, update_dt, update_user)
VALUES ('AUTH_401', 'Unauthorized', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre');