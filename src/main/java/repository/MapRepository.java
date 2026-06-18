package repository;

import database.DatabaseManager;
import model.MapPoint;
import util.ServiceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MapRepository {
    private final DatabaseManager databaseManager;

    public MapRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public long countPoints() {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM map_points");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new ServiceException("Nie można policzyć punktów mapy.", e);
        }
    }

    public MapPoint savePoint(MapPoint point) {
        String sql = "INSERT INTO map_points(name, latitude, longitude, warehouse) VALUES (?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, point.getName());
            statement.setDouble(2, point.getLatitude());
            statement.setDouble(3, point.getLongitude());
            statement.setInt(4, point.isWarehouse() ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    point.setId(keys.getLong(1));
                }
            }
            return point;
        } catch (SQLException e) {
            throw new ServiceException("Nie można zapisać punktu mapy.", e);
        }
    }

    public List<MapPoint> findAllPoints() {
        String sql = "SELECT * FROM map_points ORDER BY name";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<MapPoint> points = new ArrayList<>();
            while (rs.next()) {
                points.add(mapPoint(rs));
            }
            return points;
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać punktów mapy.", e);
        }
    }

    public Optional<MapPoint> findPointByName(String name) {
        String sql = "SELECT * FROM map_points WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapPoint(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać punktu mapy.", e);
        }
    }

    public Optional<MapPoint> findById(long id) {
        String sql = "SELECT * FROM map_points WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapPoint(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać punktu mapy.", e);
        }
    }

    public Optional<MapPoint> findWarehouse() {
        String sql = "SELECT * FROM map_points WHERE warehouse = 1 LIMIT 1";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? Optional.of(mapPoint(rs)) : Optional.empty();
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać centrali.", e);
        }
    }

    private MapPoint mapPoint(ResultSet rs) throws SQLException {
        return new MapPoint(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getInt("warehouse") == 1
        );
    }
}
