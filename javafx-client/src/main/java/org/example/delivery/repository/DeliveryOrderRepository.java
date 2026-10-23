package org.example.delivery.repository;

import org.example.delivery.exception.BusinessException;
import org.example.delivery.exception.DatabaseException;
import org.example.delivery.exception.EntityNotFoundException;
import org.example.delivery.model.DeliveryOrder;
import org.example.delivery.model.DeliveryStatus;
import org.example.delivery.util.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DeliveryOrderRepository implements CrudRepository<DeliveryOrder> {
    private static final String BASE_SELECT = """
            SELECT o.id, o.client_id, c.full_name AS client_name,
                   o.pickup_address, o.delivery_address, o.description,
                   o.weight, o.price, o.created_at, o.status
            FROM delivery_orders o
            JOIN clients c ON c.id = o.client_id
            """;

    @Override
    public DeliveryOrder save(DeliveryOrder order) {
        String sql = """
                INSERT INTO delivery_orders
                    (client_id, pickup_address, delivery_address, description, weight, price, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id, created_at
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            fillEditableFields(statement, order);
            statement.setString(7, order.getStatus().name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new DatabaseException("База данных не вернула ID нового заказа.", null);
                }
                order.setId(resultSet.getLong("id"));
                return order;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка создания заказа.", exception);
        }
    }

    @Override
    public List<DeliveryOrder> findAll() {
        return executeList(BASE_SELECT + " ORDER BY o.id");
    }

    @Override
    public Optional<DeliveryOrder> findById(long id) {
        String sql = BASE_SELECT + " WHERE o.id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка поиска заказа по ID.", exception);
        }
    }

    @Override
    public DeliveryOrder update(DeliveryOrder order) {
        String sql = """
                UPDATE delivery_orders
                SET client_id = ?, pickup_address = ?, delivery_address = ?,
                    description = ?, weight = ?, price = ?
                WHERE id = ?
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            fillEditableFields(statement, order);
            statement.setLong(7, order.getId());
            if (statement.executeUpdate() == 0) {
                throw new DatabaseException("Заказ с ID " + order.getId() + " не найден.", null);
            }
            return order;
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка изменения заказа.", exception);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM delivery_orders WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            if (statement.executeUpdate() == 0) {
                throw new DatabaseException("Заказ с ID " + id + " не найден.", null);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка удаления заказа.", exception);
        }
    }

    public void changeStatus(long id, DeliveryStatus target) {
        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                DeliveryStatus current = lockStatus(connection, id);
                if (!current.canTransitionTo(target)) {
                    throw new BusinessException(
                            "Недопустимый переход статуса: %s -> %s".formatted(current, target));
                }
                writeStatus(connection, id, target);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка смены статуса заказа.", exception);
        }
    }

    private DeliveryStatus lockStatus(Connection connection, long id) throws SQLException {
        String sql = "SELECT status FROM delivery_orders WHERE id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new EntityNotFoundException("Заказ с ID " + id + " не найден.");
                }
                return DeliveryStatus.valueOf(resultSet.getString("status"));
            }
        }
    }

    private void writeStatus(Connection connection, long id, DeliveryStatus status) throws SQLException {
        String sql = "UPDATE delivery_orders SET status = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public List<DeliveryOrder> search(String query) {
        String sql = BASE_SELECT + """
                WHERE LOWER(c.full_name) LIKE LOWER(?)
                   OR c.phone LIKE ?
                   OR LOWER(o.pickup_address) LIKE LOWER(?)
                   OR LOWER(o.delivery_address) LIKE LOWER(?)
                ORDER BY o.created_at DESC
                """;
        String pattern = "%" + query.trim() + "%";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) {
                statement.setString(i, pattern);
            }
            return readList(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка поиска заказов.", exception);
        }
    }

    public List<DeliveryOrder> filter(DeliveryStatus status, BigDecimal minPrice, BigDecimal maxPrice) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (status != null) {
            sql.append(" AND o.status = ?");
            parameters.add(status.name());
        }
        if (minPrice != null) {
            sql.append(" AND o.price >= ?");
            parameters.add(minPrice);
        }
        if (maxPrice != null) {
            sql.append(" AND o.price <= ?");
            parameters.add(maxPrice);
        }
        sql.append(" ORDER BY o.created_at DESC");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }
            return readList(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка фильтрации заказов.", exception);
        }
    }

    public List<DeliveryOrder> sort(String sortBy, boolean descending) {
        String column = switch (sortBy) {
            case "price" -> "o.price";
            case "weight" -> "o.weight";
            case "created_at" -> "o.created_at";
            default -> "o.id";
        };
        String direction = descending ? " DESC" : " ASC";
        return executeList(BASE_SELECT + " ORDER BY " + column + direction);
    }

    public long countByStatus(DeliveryStatus status) {
        String sql = "SELECT COUNT(*) FROM delivery_orders WHERE status = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка получения статистики заказов.", exception);
        }
    }

    public BigDecimal totalRevenue() {
        String sql = "SELECT COALESCE(SUM(price), 0) FROM delivery_orders WHERE status = 'DELIVERED'";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getBigDecimal(1);
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка расчета выручки.", exception);
        }
    }

    public BigDecimal averagePrice() {
        return aggregateDecimal("SELECT COALESCE(AVG(price), 0) FROM delivery_orders",
                "Ошибка расчета средней стоимости заказа.");
    }

    public BigDecimal averageWeight() {
        return aggregateDecimal("SELECT COALESCE(AVG(weight), 0) FROM delivery_orders",
                "Ошибка расчета среднего веса заказа.");
    }

    public BigDecimal minPrice() {
        return aggregateDecimal("SELECT COALESCE(MIN(price), 0) FROM delivery_orders",
                "Ошибка поиска минимальной стоимости заказа.");
    }

    public BigDecimal maxPrice() {
        return aggregateDecimal("SELECT COALESCE(MAX(price), 0) FROM delivery_orders",
                "Ошибка поиска максимальной стоимости заказа.");
    }

    private BigDecimal aggregateDecimal(String sql, String errorMessage) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getBigDecimal(1);
        } catch (SQLException exception) {
            throw new DatabaseException(errorMessage, exception);
        }
    }

    private List<DeliveryOrder> executeList(String sql) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return readList(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка получения заказов.", exception);
        }
    }

    private List<DeliveryOrder> readList(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            List<DeliveryOrder> orders = new ArrayList<>();
            while (resultSet.next()) {
                orders.add(map(resultSet));
            }
            return orders;
        }
    }

    private void fillEditableFields(PreparedStatement statement, DeliveryOrder order) throws SQLException {
        statement.setLong(1, order.getClientId());
        statement.setString(2, order.getPickupAddress());
        statement.setString(3, order.getDeliveryAddress());
        statement.setString(4, order.getDescription());
        statement.setBigDecimal(5, order.getWeight());
        statement.setBigDecimal(6, order.getPrice());
    }

    private DeliveryOrder map(ResultSet resultSet) throws SQLException {
        return new DeliveryOrder(
                resultSet.getLong("id"),
                resultSet.getLong("client_id"),
                resultSet.getString("client_name"),
                resultSet.getString("pickup_address"),
                resultSet.getString("delivery_address"),
                resultSet.getString("description"),
                resultSet.getBigDecimal("weight"),
                resultSet.getBigDecimal("price"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                DeliveryStatus.valueOf(resultSet.getString("status")));
    }
}
