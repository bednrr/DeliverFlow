package repository;

import database.DatabaseManager;
import model.Theme;
import model.User;
import model.UserRole;
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

public class UserRepository {
    private final DatabaseManager databaseManager;

    public UserRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public long count() {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new ServiceException("Nie można policzyć użytkowników.", e);
        }
    }

    public User save(User user) {
        String sql = """
                INSERT INTO users(first_name, last_name, email, phone, password, role, theme, blocked, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String now = TimeUtil.nowDb();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getFirstName());
            statement.setString(2, user.getLastName());
            statement.setString(3, user.getEmail().toLowerCase());
            statement.setString(4, user.getPhone());
            statement.setString(5, user.getPassword());
            statement.setString(6, user.getRole().name());
            statement.setString(7, user.getTheme() == null ? Theme.LIGHT.name() : user.getTheme().name());
            statement.setInt(8, user.isBlocked() ? 1 : 0);
            statement.setString(9, now);
            statement.setString(10, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                }
            }
            return findById(user.getId()).orElseThrow();
        } catch (SQLException e) {
            throw new ServiceException("Nie można zapisać użytkownika.", e);
        }
    }

    public void update(User user) {
        String sql = """
                UPDATE users
                SET first_name = ?, last_name = ?, email = ?, phone = ?, password = ?, role = ?, theme = ?, blocked = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getFirstName());
            statement.setString(2, user.getLastName());
            statement.setString(3, user.getEmail().toLowerCase());
            statement.setString(4, user.getPhone());
            statement.setString(5, user.getPassword());
            statement.setString(6, user.getRole().name());
            statement.setString(7, user.getTheme().name());
            statement.setInt(8, user.isBlocked() ? 1 : 0);
            statement.setString(9, TimeUtil.nowDb());
            statement.setLong(10, user.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Nie można zaktualizować użytkownika.", e);
        }
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE lower(email) = lower(?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać użytkownika.", e);
        }
    }

    public Optional<User> findById(long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać użytkownika.", e);
        }
    }

    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY role, last_name, first_name";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<User> users = new ArrayList<>();
            while (rs.next()) {
                users.add(map(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać listy użytkowników.", e);
        }
    }

    public boolean emailExistsForAnotherUser(String email, long currentUserId) {
        String sql = "SELECT id FROM users WHERE lower(email) = lower(?) AND id <> ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setLong(2, currentUserId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można sprawdzić adresu e-mail.", e);
        }
    }

    private User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("password"),
                UserRole.valueOf(rs.getString("role")),
                Theme.valueOf(rs.getString("theme")),
                rs.getInt("blocked") == 1,
                Db.dateTime(rs, "created_at"),
                Db.dateTime(rs, "updated_at")
        );
    }
}
