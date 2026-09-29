CREATE TABLE orders (
                        id BIGSERIAL PRIMARY KEY,
                        client_id BIGINT NOT NULL REFERENCES users(id),
                        driver_id BIGINT REFERENCES users(id),
                        status VARCHAR(20) NOT NULL,
                        from_address VARCHAR(500) NOT NULL,
                        to_address VARCHAR(500) NOT NULL,
                        price NUMERIC(10, 2),
                        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                        updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_client ON orders(client_id);
CREATE INDEX idx_orders_status ON orders(status);