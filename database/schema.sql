\encoding UTF8

CREATE TABLE IF NOT EXISTS clients (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NOT NULL UNIQUE,
    email VARCHAR(150) UNIQUE,
    address TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS delivery_orders (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL,
    pickup_address TEXT NOT NULL,
    delivery_address TEXT NOT NULL,
    description TEXT,
    weight NUMERIC(10, 2) NOT NULL CHECK (weight > 0),
    price NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    CONSTRAINT fk_delivery_orders_client
        FOREIGN KEY (client_id) REFERENCES clients(id),
    CONSTRAINT chk_delivery_orders_status
        CHECK (status IN (
            'CREATED',
            'CONFIRMED',
            'ASSIGNED_TO_COURIER',
            'PICKED_UP',
            'IN_TRANSIT',
            'DELIVERED',
            'CANCELLED'
        ))
);

CREATE INDEX IF NOT EXISTS idx_delivery_orders_client_id
    ON delivery_orders(client_id);

CREATE INDEX IF NOT EXISTS idx_delivery_orders_status
    ON delivery_orders(status);

CREATE INDEX IF NOT EXISTS idx_delivery_orders_created_at
    ON delivery_orders(created_at);
