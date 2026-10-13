INSERT INTO order_status_lkup (
    code, name, description, status,
    create_dt, create_user, update_dt, update_user, guid
)
VALUES
    ('1.00', 'Created', 'Initial Order Status', 'A', now(), 'Andre', now(), 'Andre', gen_random_uuid()),
    ('1.01', 'Quoting', 'Order is being quoted', 'A', now(), 'Andre', now(), 'Andre', gen_random_uuid()),
    ('2.00', 'Confirmed', 'Order has been confirmed', 'A', now(), 'Andre', now(), 'Andre', gen_random_uuid()),
    ('2.01', 'In Production', 'Order is in production', 'A', now(), 'Andre', now(), 'Andre', gen_random_uuid()),
    ('2.02', 'In Transit', 'Order is in transit', 'A', now(), 'Andre', now(), 'Andre', gen_random_uuid()),
    ('3.00', 'Completed', 'Order has been completed', 'A', now(), 'Andre', now(), 'Andre', gen_random_uuid()),
    ('9.00', 'Cancelled', 'Order has been cancelled', 'A', now(), 'Andre', now(), 'Andre', gen_random_uuid()),
    ('9.01', 'Error', 'An error occurred while processing the order', 'A', now(), 'Andre', now(), 'Andre', gen_random_uuid());