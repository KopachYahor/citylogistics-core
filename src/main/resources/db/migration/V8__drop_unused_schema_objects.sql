-- Убираем то, что не используется.
-- order_status_history создана в V3, но код никогда в неё не писал.
DROP TABLE IF EXISTS order_status_history;
-- idx_users_email дублирует индекс users_email_key, который PostgreSQL сам создал для UNIQUE(email).
DROP INDEX IF EXISTS idx_users_email;
