package repository;

import database.DatabaseManager;
import model.Notification;
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

public class NotificationRepository {
    private final DatabaseManager databaseManager;

    public NotificationRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Notification create(long senderUserId, long recipientUserId, String title, String message) {
        String sql = """
                INSERT INTO notifications(sender_user_id, recipient_user_id, title, message, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, senderUserId);
            statement.setLong(2, recipientUserId);
            statement.setString(3, title);
            statement.setString(4, message);
            statement.setString(5, TimeUtil.nowDb());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getLong(1)).orElseThrow();
                }
            }
            throw new ServiceException("Nie można odczytać utworzonego powiadomienia.");
        } catch (SQLException e) {
            throw new ServiceException("Nie można zapisać powiadomienia.", e);
        }
    }

    public List<Notification> findForRecipient(long recipientUserId) {
        String sql = """
                SELECT n.*,
                       %s AS sender_name,
                       %s AS recipient_name
                FROM notifications n
                JOIN users sender ON sender.id = n.sender_user_id
                JOIN users recipient ON recipient.id = n.recipient_user_id
                WHERE n.recipient_user_id = ?
                ORDER BY n.created_at DESC, n.id DESC
                """.formatted(
                databaseManager.fullNameExpression("sender.first_name", "sender.last_name"),
                databaseManager.fullNameExpression("recipient.first_name", "recipient.last_name")
        );
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientUserId);
            try (ResultSet rs = statement.executeQuery()) {
                List<Notification> notifications = new ArrayList<>();
                while (rs.next()) {
                    notifications.add(map(rs));
                }
                return notifications;
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać powiadomień.", e);
        }
    }

    public Optional<Notification> findById(long id) {
        String sql = """
                SELECT n.*,
                       %s AS sender_name,
                       %s AS recipient_name
                FROM notifications n
                JOIN users sender ON sender.id = n.sender_user_id
                JOIN users recipient ON recipient.id = n.recipient_user_id
                WHERE n.id = ?
                """.formatted(
                databaseManager.fullNameExpression("sender.first_name", "sender.last_name"),
                databaseManager.fullNameExpression("recipient.first_name", "recipient.last_name")
        );
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać powiadomienia.", e);
        }
    }

    public void markRead(long notificationId, long recipientUserId) {
        String sql = """
                UPDATE notifications
                SET read_at = COALESCE(read_at, ?)
                WHERE id = ? AND recipient_user_id = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TimeUtil.nowDb());
            statement.setLong(2, notificationId);
            statement.setLong(3, recipientUserId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Nie można oznaczyć powiadomienia jako przeczytanego.", e);
        }
    }

    public void markAllRead(long recipientUserId) {
        String sql = """
                UPDATE notifications
                SET read_at = COALESCE(read_at, ?)
                WHERE recipient_user_id = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TimeUtil.nowDb());
            statement.setLong(2, recipientUserId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Nie można oznaczyć powiadomień jako przeczytanych.", e);
        }
    }

    private Notification map(ResultSet rs) throws SQLException {
        return new Notification(
                rs.getLong("id"),
                rs.getLong("sender_user_id"),
                rs.getString("sender_name"),
                rs.getLong("recipient_user_id"),
                rs.getString("recipient_name"),
                rs.getString("title"),
                rs.getString("message"),
                Db.dateTime(rs, "created_at"),
                Db.dateTime(rs, "read_at")
        );
    }
}
