\encoding UTF8

INSERT INTO clients (full_name, phone, email, address)
VALUES
    ('Иванов Иван Иванович', '+79990000001', 'ivanov@example.com', 'Москва, ул. Ленина, 1'),
    ('Петрова Анна Сергеевна', '+79990000002', 'petrova@example.com', 'Москва, ул. Мира, 2'),
    ('Сидоров Алексей Олегович', '+79990000003', 'sidorov@example.com', 'Москва, ул. Гагарина, 3'),
    ('Кузнецова Мария Игоревна', '+79990000004', 'kuznetsova@example.com', 'Москва, ул. Чехова, 4'),
    ('Смирнов Дмитрий Павлович', '+79990000005', 'smirnov@example.com', 'Москва, ул. Пушкина, 5')
ON CONFLICT (phone) DO NOTHING;

INSERT INTO delivery_orders (
    client_id, pickup_address, delivery_address, description, weight, price, status
)
SELECT c.id, v.pickup_address, v.delivery_address, v.description,
       v.weight, v.price, v.status
FROM (
    VALUES
        ('+79990000001', 'Москва, ул. Ленина, 1', 'Москва, ул. Мира, 2', 'Документы', 0.50, 450.00, 'CREATED'),
        ('+79990000001', 'Москва, ул. Ленина, 1', 'Москва, ул. Гагарина, 3', 'Книга', 1.20, 650.00, 'CONFIRMED'),
        ('+79990000002', 'Москва, ул. Мира, 2', 'Москва, ул. Чехова, 4', 'Одежда', 2.50, 900.00, 'ASSIGNED_TO_COURIER'),
        ('+79990000002', 'Москва, ул. Мира, 2', 'Москва, ул. Пушкина, 5', 'Посылка', 4.00, 1200.00, 'PICKED_UP'),
        ('+79990000003', 'Москва, ул. Гагарина, 3', 'Москва, ул. Ленина, 1', 'Запчасти', 3.70, 1100.00, 'IN_TRANSIT'),
        ('+79990000003', 'Москва, ул. Гагарина, 3', 'Москва, ул. Мира, 2', 'Подарок', 1.80, 700.00, 'DELIVERED'),
        ('+79990000004', 'Москва, ул. Чехова, 4', 'Москва, ул. Пушкина, 5', 'Техника', 5.00, 1600.00, 'DELIVERED'),
        ('+79990000004', 'Москва, ул. Чехова, 4', 'Москва, ул. Ленина, 1', 'Канцелярия', 2.10, 750.00, 'CANCELLED'),
        ('+79990000005', 'Москва, ул. Пушкина, 5', 'Москва, ул. Гагарина, 3', 'Продукты', 6.50, 1800.00, 'CREATED'),
        ('+79990000005', 'Москва, ул. Пушкина, 5', 'Москва, ул. Чехова, 4', 'Цветы', 1.00, 550.00, 'CONFIRMED')
) AS v(phone, pickup_address, delivery_address, description, weight, price, status)
JOIN clients c ON c.phone = v.phone
WHERE NOT EXISTS (
    SELECT 1
    FROM delivery_orders existing
    WHERE existing.client_id = c.id
      AND existing.pickup_address = v.pickup_address
      AND existing.delivery_address = v.delivery_address
      AND existing.description = v.description
);
