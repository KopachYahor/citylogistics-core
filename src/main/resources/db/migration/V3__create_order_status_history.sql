CREATE TABLE order_status_history (
                                      id BIGSERIAL PRIMARY KEY,
                                      order_id BIGINT NOT NULL REFERENCES orders(id),
                                      status VARCHAR(20) NOT NULL,
                                      changed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_status_history_order ON order_status_history(order_id);