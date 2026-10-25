ALTER TABLE users
    ADD COLUMN driver_id BIGINT;

CREATE INDEX idx_users_driver_id ON users(driver_id);