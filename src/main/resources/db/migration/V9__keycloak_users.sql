-- Вход теперь через Keycloak: пароли хранит он, у нас — только связь с его пользователем.
-- keycloak_id = sub в токене (UUID пользователя в Keycloak). NULL допустим у старых строк,
-- созданных до перехода: войти они не смогут, для локальной разработки базы пересоздаются.
ALTER TABLE users ADD COLUMN keycloak_id VARCHAR(36);
ALTER TABLE users ADD CONSTRAINT uk_users_keycloak_id UNIQUE (keycloak_id);

ALTER TABLE users DROP COLUMN password_hash;
