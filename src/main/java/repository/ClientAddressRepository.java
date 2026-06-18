package repository;

import database.DatabaseManager;
import model.ClientAddress;
import util.Db;
import util.ServiceException;
import util.TimeUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClientAddressRepository {
    private final DatabaseManager databaseManager;

    public ClientAddressRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public ClientAddress save(ClientAddress address) {
        String sql = """
                INSERT INTO client_addresses(user_id, name, address_text, map_point_id, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        String now = TimeUtil.nowDb();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, address.getUserId());
            statement.setString(2, address.getName());
            statement.setString(3, address.getAddressText());
            statement.setLong(4, address.getMapPointId());
            statement.setString(5, address.getNotes());
            statement.setString(6, now);
            statement.setString(7, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    address.setId(keys.getLong(1));
                }
            }
            return findById(address.getId()).orElseThrow();
        } catch (SQLException e) {
            throw new ServiceException("Nie można zapisać adresu.", e);
        }
    }

    public void update(ClientAddress address) {
        String sql = """
                UPDATE client_addresses
                SET name = ?, address_text = ?, map_point_id = ?, notes = ?, updated_at = ?
                WHERE id = ? AND user_id = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, address.getName());
            statement.setString(2, address.getAddressText());
            statement.setLong(3, address.getMapPointId());
            statement.setString(4, address.getNotes());
            statement.setString(5, TimeUtil.nowDb());
            statement.setLong(6, address.getId());
            statement.setLong(7, address.getUserId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Nie można zaktualizować adresu.", e);
        }
    }

    public Optional<ClientAddress> findById(long id) {
        String sql = "SELECT * FROM client_addresses WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać adresu.", e);
        }
    }

    public List<ClientAddress> findByUserId(long userId) {
        String sql = "SELECT * FROM client_addresses WHERE user_id = ? ORDER BY name";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                List<ClientAddress> addresses = new ArrayList<>();
                while (rs.next()) {
                    addresses.add(map(rs));
                }
                return addresses;
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać adresów.", e);
        }
    }

    public boolean nameExistsForUser(long userId, String name, Long ignoredId) {
        String sql = """
                SELECT COUNT(*) FROM client_addresses
                WHERE user_id = ? AND lower(trim(name)) = lower(trim(?))
                """ + (ignoredId == null ? "" : " AND id <> ?");
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, name);
            if (ignoredId != null) {
                statement.setLong(3, ignoredId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można sprawdzić nazwy adresu.", e);
        }
    }

    public void delete(long id, long userId) {
        String sql = "DELETE FROM client_addresses WHERE id = ? AND user_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, userId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Nie można usunąć adresu.", e);
        }
    }

    private ClientAddress map(ResultSet rs) throws SQLException {
        return new ClientAddress(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("name"),
                rs.getString("address_text"),
                rs.getLong("map_point_id"),
                rs.getString("notes"),
                Db.dateTime(rs, "created_at"),
                Db.dateTime(rs, "updated_at")
        );
    }
}
