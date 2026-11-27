-- КР №3. Развитие базы данных КР №1 под REST backend.
--
-- Что меняется:
--   * clients и delivery_orders переходят с BIGSERIAL на UUID (существующие данные сохраняются);
--   * у заказа появляются номер, хрупкость, приоритет, архив, version, авторы изменений, теги;
--   * у клиента появляются признак активности, временные метки и version;
--   * добавляются пользователи, роли, refresh-сессии, комментарии и журнал аудита.

-- ============================================================
-- 1. Пользователи, роли, refresh-сессии
-- ============================================================
CREATE TABLE app_user (
    id                       UUID PRIMARY KEY,
    email                    VARCHAR(254) NOT NULL UNIQUE,
    display_name             VARCHAR(160) NOT NULL,
    password_hash            VARCHAR(100) NOT NULL,
    enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    password_change_required BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts    INTEGER      NOT NULL DEFAULT 0,
    locked_until             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE app_user_role (
    user_id UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT chk_app_user_role CHECK (role IN ('VIEWER', 'DISPATCHER', 'ADMIN'))
);

CREATE TABLE refresh_session (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID,
    user_agent   VARCHAR(500),
    ip_address   VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ
);

CREATE INDEX idx_refresh_user ON refresh_session(user_id);
CREATE INDEX idx_refresh_expires ON refresh_session(expires_at);

-- ============================================================
-- 2. clients: BIGSERIAL -> UUID, активность и служебные поля
-- ============================================================
ALTER TABLE clients ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE clients ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE clients ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE clients ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE clients ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ============================================================
-- 3. delivery_orders: новые поля и перенос связи на UUID клиента
-- ============================================================
ALTER TABLE delivery_orders ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE delivery_orders ADD COLUMN client_uuid UUID;
ALTER TABLE delivery_orders ADD COLUMN order_number VARCHAR(40);
ALTER TABLE delivery_orders ADD COLUMN fragile BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE delivery_orders ADD COLUMN priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE delivery_orders ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE delivery_orders ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE delivery_orders ADD COLUMN archived_by UUID REFERENCES app_user(id);
ALTER TABLE delivery_orders ADD COLUMN created_by UUID REFERENCES app_user(id);
ALTER TABLE delivery_orders ADD COLUMN updated_by UUID REFERENCES app_user(id);
ALTER TABLE delivery_orders ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE delivery_orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- created_at в КР №1 был TIMESTAMP без зоны; трактуем старые значения как московское время.
ALTER TABLE delivery_orders
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Europe/Moscow',
    ALTER COLUMN created_at DROP DEFAULT;

UPDATE delivery_orders o
SET client_uuid  = c.uuid,
    order_number = 'DLV-' || lpad(o.id::text, 6, '0'),
    updated_at   = o.created_at
FROM clients c
WHERE c.id = o.client_id;

-- Приоритет для существующих заказов по тому же правилу, что и PriorityCalculator.
UPDATE delivery_orders
SET priority = CASE
    WHEN status IN ('DELIVERED', 'CANCELLED') THEN 'NOT_APPLICABLE'
    ELSE CASE
        WHEN score >= 4 THEN 'URGENT'
        WHEN score = 3 THEN 'HIGH'
        WHEN score >= 1 THEN 'NORMAL'
        ELSE 'LOW'
    END
END
FROM (
    SELECT id AS sid,
           (CASE WHEN fragile THEN 2 ELSE 0 END)
         + (CASE WHEN price >= 1500 THEN 2 WHEN price >= 800 THEN 1 ELSE 0 END)
         + (CASE WHEN weight >= 5 THEN 1 ELSE 0 END)
         + (CASE WHEN status IN ('PICKED_UP', 'IN_TRANSIT') THEN 1 ELSE 0 END) AS score
    FROM delivery_orders
) s
WHERE s.sid = delivery_orders.id;

-- Переключаем первичные и внешние ключи на UUID.
ALTER TABLE delivery_orders DROP CONSTRAINT fk_delivery_orders_client;
DROP INDEX IF EXISTS idx_delivery_orders_client_id;
ALTER TABLE delivery_orders DROP COLUMN client_id;
ALTER TABLE delivery_orders RENAME COLUMN client_uuid TO client_id;
ALTER TABLE delivery_orders ALTER COLUMN client_id SET NOT NULL;

ALTER TABLE clients DROP CONSTRAINT clients_pkey;
ALTER TABLE clients DROP COLUMN id;
ALTER TABLE clients RENAME COLUMN uuid TO id;
ALTER TABLE clients ALTER COLUMN id DROP DEFAULT;
ALTER TABLE clients ADD PRIMARY KEY (id);

ALTER TABLE delivery_orders DROP CONSTRAINT delivery_orders_pkey;
ALTER TABLE delivery_orders DROP COLUMN id;
ALTER TABLE delivery_orders RENAME COLUMN uuid TO id;
ALTER TABLE delivery_orders ALTER COLUMN id DROP DEFAULT;
ALTER TABLE delivery_orders ADD PRIMARY KEY (id);

ALTER TABLE delivery_orders
    ADD CONSTRAINT fk_delivery_orders_client FOREIGN KEY (client_id) REFERENCES clients(id),
    ALTER COLUMN order_number SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN priority DROP DEFAULT,
    ADD CONSTRAINT uq_delivery_orders_number UNIQUE (order_number),
    ADD CONSTRAINT chk_delivery_orders_priority
        CHECK (priority IN ('NOT_APPLICABLE', 'LOW', 'NORMAL', 'HIGH', 'URGENT')),
    ADD CONSTRAINT chk_delivery_orders_weight_max CHECK (weight <= 10000),
    ADD CONSTRAINT chk_delivery_orders_archive
        CHECK ((archived AND archived_at IS NOT NULL) OR (NOT archived AND archived_at IS NULL));

ALTER TABLE clients
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT;

-- Индексы для поиска и фильтрации.
CREATE INDEX idx_delivery_orders_client_id ON delivery_orders(client_id);
CREATE INDEX idx_delivery_orders_archived ON delivery_orders(archived);
CREATE INDEX idx_delivery_orders_priority ON delivery_orders(priority);
CREATE INDEX idx_delivery_orders_fragile ON delivery_orders(fragile);
CREATE INDEX idx_delivery_orders_price ON delivery_orders(price);
CREATE INDEX idx_clients_full_name ON clients(lower(full_name));
CREATE INDEX idx_clients_active ON clients(active);

-- ============================================================
-- 4. Теги и комментарии заказов
-- ============================================================
CREATE TABLE order_tag (
    order_id UUID        NOT NULL REFERENCES delivery_orders(id) ON DELETE CASCADE,
    tag      VARCHAR(50) NOT NULL,
    PRIMARY KEY (order_id, tag)
);

CREATE INDEX idx_order_tag_value ON order_tag(tag);

CREATE TABLE order_note (
    id         UUID PRIMARY KEY,
    order_id   UUID          NOT NULL REFERENCES delivery_orders(id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL REFERENCES app_user(id),
    body       VARCHAR(4000) NOT NULL,
    pinned     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    version    BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_order_note_order ON order_note(order_id, pinned DESC, created_at DESC);

-- Несколько тегов для демонстрационных данных КР №1.
INSERT INTO order_tag (order_id, tag)
SELECT id, 'документы' FROM delivery_orders WHERE description = 'Документы'
UNION ALL
SELECT id, 'техника' FROM delivery_orders WHERE description = 'Техника'
UNION ALL
SELECT id, 'срочно' FROM delivery_orders WHERE description IN ('Документы', 'Цветы', 'Продукты');

UPDATE delivery_orders SET fragile = TRUE WHERE description IN ('Техника', 'Цветы', 'Подарок');

-- Хрупкость изменила баллы — пересчитываем приоритет для затронутых заказов.
UPDATE delivery_orders
SET priority = CASE
    WHEN status IN ('DELIVERED', 'CANCELLED') THEN 'NOT_APPLICABLE'
    WHEN (2 + CASE WHEN price >= 1500 THEN 2 WHEN price >= 800 THEN 1 ELSE 0 END
            + CASE WHEN weight >= 5 THEN 1 ELSE 0 END
            + CASE WHEN status IN ('PICKED_UP', 'IN_TRANSIT') THEN 1 ELSE 0 END) >= 4 THEN 'URGENT'
    WHEN (2 + CASE WHEN price >= 1500 THEN 2 WHEN price >= 800 THEN 1 ELSE 0 END
            + CASE WHEN weight >= 5 THEN 1 ELSE 0 END
            + CASE WHEN status IN ('PICKED_UP', 'IN_TRANSIT') THEN 1 ELSE 0 END) = 3 THEN 'HIGH'
    ELSE 'NORMAL'
END
WHERE fragile;

-- ============================================================
-- 5. Журнал аудита
-- ============================================================
CREATE TABLE audit_event (
    id             UUID PRIMARY KEY,
    actor_id       UUID,
    actor_email    VARCHAR(254),
    event_type     VARCHAR(80)   NOT NULL,
    entity_type    VARCHAR(80),
    entity_id      UUID,
    summary        VARCHAR(1000) NOT NULL,
    correlation_id VARCHAR(100),
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(500),
    occurred_at    TIMESTAMPTZ   NOT NULL,
    details_json   TEXT
);

CREATE INDEX idx_audit_occurred ON audit_event(occurred_at DESC);
CREATE INDEX idx_audit_actor ON audit_event(actor_email);
CREATE INDEX idx_audit_entity ON audit_event(entity_type, entity_id);
CREATE INDEX idx_audit_event_type ON audit_event(event_type);
