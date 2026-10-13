ALTER TABLE orders ADD COLUMN IF NOT EXISTS driver_id BIGINT;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS fk_orders_driver;
ALTER TABLE orders ADD CONSTRAINT fk_orders_driver
    FOREIGN KEY (driver_id) REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_orders_driver_id ON orders(driver_id);