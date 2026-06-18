package repository;

import database.DatabaseManager;
import model.EventLog;
import util.Db;
import util.ServiceException;
import util.TimeUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EventLogRepository {
    private final DatabaseManager databaseManager;

    public EventLogRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void add(String level, String message) {
        String sql = "INSERT INTO event_logs(level, message, created_at) VALUES (?, ?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, level);
            statement.setString(2, message);
            statement.setString(3, TimeUtil.nowDb());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Nie można zapisać zdarzenia.", e);
        }
    }

    public List<EventLog> latest(int limit) {
        String sql = "SELECT * FROM event_logs ORDER BY created_at DESC, id DESC LIMIT ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<EventLog> logs = new ArrayList<>();
                while (rs.next()) {
                    logs.add(new EventLog(
                            rs.getLong("id"),
                            rs.getString("level"),
                            rs.getString("message"),
                            Db.dateTime(rs, "created_at")
                    ));
                }
                return logs;
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać zdarzeń.", e);
        }
    }
}
