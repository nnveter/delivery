package org.example.delivery.repository;

import org.example.delivery.exception.DatabaseException;
import org.example.delivery.model.Client;
import org.example.delivery.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ClientRepository implements CrudRepository<Client> {
    private static final String BASE_SELECT =
            "SELECT id, full_name, phone, email, address FROM clients";

    @Override
    public Client save(Client client) {
        String sql = """
                INSERT INTO clients (full_name, phone, email, address)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, client.getFullName());
            statement.setString(2, client.getPhone());
            statement.setString(3, client.getEmail());
            statement.setString(4, client.getAddress());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new DatabaseException("База данных не вернула ID нового клиента.", null);
                }
                client.setId(resultSet.getLong("id"));
                return client;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка создания клиента.", exception);
        }
    }

    @Override
    public List<Client> findAll() {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(BASE_SELECT + " ORDER BY id");
             ResultSet resultSet = statement.executeQuery()) {
            List<Client> clients = new ArrayList<>();
            while (resultSet.next()) {
                clients.add(map(resultSet));
            }
            return clients;
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка получения списка клиентов.", exception);
        }
    }

    @Override
    public Optional<Client> findById(long id) {
        String sql = BASE_SELECT + " WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка поиска клиента по ID.", exception);
        }
    }

    @Override
    public Client update(Client client) {
        String sql = """
                UPDATE clients
                SET full_name = ?, phone = ?, email = ?, address = ?
                WHERE id = ?
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, client.getFullName());
            statement.setString(2, client.getPhone());
            statement.setString(3, client.getEmail());
            statement.setString(4, client.getAddress());
            statement.setLong(5, client.getId());
            if (statement.executeUpdate() == 0) {
                throw new DatabaseException("Клиент с ID " + client.getId() + " не найден.", null);
            }
            return client;
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка изменения клиента.", exception);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM clients WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            if (statement.executeUpdate() == 0) {
                throw new DatabaseException("Клиент с ID " + id + " не найден.", null);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка удаления клиента. Возможно, у него есть заказы.", exception);
        }
    }

    public List<Client> search(String query) {
        String sql = BASE_SELECT + """
                WHERE LOWER(full_name) LIKE LOWER(?)
                   OR phone LIKE ?
                   OR LOWER(COALESCE(email, '')) LIKE LOWER(?)
                ORDER BY full_name
                """;
        String pattern = "%" + query.trim() + "%";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Client> clients = new ArrayList<>();
                while (resultSet.next()) {
                    clients.add(map(resultSet));
                }
                return clients;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Ошибка поиска клиентов.", exception);
        }
    }

    private Client map(ResultSet resultSet) throws SQLException {
        return new Client(
                resultSet.getLong("id"),
                resultSet.getString("full_name"),
                resultSet.getString("phone"),
                resultSet.getString("email"),
                resultSet.getString("address"));
    }
}
