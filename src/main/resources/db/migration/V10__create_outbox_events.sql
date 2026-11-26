-- Transactional Outbox: событие для Kafka сначала записывается сюда в той же транзакции,
-- что и изменение заказа. OutboxRelay отправляет его в Kafka и ставит sent_at.
CREATE TABLE outbox_events (
    id          BIGSERIAL PRIMARY KEY,
    topic       VARCHAR(100) NOT NULL,
    message_key VARCHAR(100) NOT NULL,  -- ключ сообщения Kafka (id заказа)
    event_type  VARCHAR(255) NOT NULL,  -- полное имя класса события: уходит в заголовок __TypeId__
    payload     TEXT         NOT NULL,  -- событие в JSON
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    sent_at     TIMESTAMP               -- NULL, пока событие не принято Kafka
);

-- Relay каждую секунду ищет неотправленные события по порядку id.
-- Частичный индекс содержит только их, поэтому не растёт вместе с историей
CREATE INDEX idx_outbox_events_unsent ON outbox_events (id) WHERE sent_at IS NULL;
