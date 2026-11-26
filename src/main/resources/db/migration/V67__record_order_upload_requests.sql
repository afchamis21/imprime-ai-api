CREATE TABLE order_upload_request
(
    buyer_id BIGINT NOT NULL REFERENCES users (user_id),
    request_id UUID NOT NULL,
    request_scope VARCHAR(80) NOT NULL,
    order_id BIGINT REFERENCES orders (order_id),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (buyer_id, request_id)
);
