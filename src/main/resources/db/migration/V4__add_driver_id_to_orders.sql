ALTER TABLE orders
    ADD COLUMN driver_id BIGINT;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_driver
        FOREIGN KEY (driver_id) REFERENCES users(id);

CREATE INDEX idx_orders_driver_id ON orders(driver_id);