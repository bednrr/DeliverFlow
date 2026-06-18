package repository;

import database.DatabaseManager;
import model.Courier;
import model.CourierStatus;
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

public class CourierRepository {
    private final DatabaseManager databaseManager;

    public CourierRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Courier save(Courier courier) {
        String sql = """
                INSERT INTO couriers(
                    user_id, status, current_map_point_id, vehicle_number, updated_at,
                    current_latitude, current_longitude, current_location_label,
                    shift_start, shift_end, test_mode_enabled
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, courier.getUserId());
            statement.setString(2, courier.getStatus().name());
            statement.setLong(3, courier.getCurrentMapPointId());
            statement.setString(4, courier.getVehicleNumber());
            statement.setString(5, TimeUtil.nowDb());
            setNullableDouble(statement, 6, courier.getCurrentLatitude());
            setNullableDouble(statement, 7, courier.getCurrentLongitude());
            statement.setString(8, courier.getCurrentPointName());
            statement.setString(9, defaultShift(courier.getShiftStart(), "06:00"));
            statement.setString(10, defaultShift(courier.getShiftEnd(), "14:00"));
            statement.setInt(11, courier.isTestModeEnabled() ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    courier.setId(keys.getLong(1));
                }
            }
            return findById(courier.getId()).orElseThrow();
        } catch (SQLException e) {
            throw new ServiceException("Nie można zapisać kuriera.", e);
        }
    }

    public Optional<Courier> findById(long id) {
        return findSingle("WHERE c.id = ?", id);
    }

    public Optional<Courier> findByUserId(long userId) {
        return findSingle("WHERE c.user_id = ?", userId);
    }

    public List<Courier> findAll() {
        return findMany("");
    }

    public List<Courier> findAssignable() {
        return findMany("""
                WHERE c.status = 'AVAILABLE'
                AND NOT EXISTS (
                    SELECT 1
                    FROM parcels p
                    WHERE p.assigned_courier_id = c.id
                    AND p.status NOT IN ('DELIVERED', 'CANCELED', 'DELIVERY_PROBLEM')
                )
                """);
    }

    public void updateStatus(long courierId, CourierStatus status) {
        String sql = "UPDATE couriers SET status = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setString(2, TimeUtil.nowDb());
            statement.setLong(3, courierId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Nie można zmienić statusu kuriera.", e);
        }
    }

    public void updateVehicle(long courierId, String vehicleNumber) {
        String sql = "UPDATE couriers SET vehicle_number = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vehicleNumber == null || vehicleNumber.isBlank() ? "DF-" + courierId : vehicleNumber.trim());
            statement.setString(2, TimeUtil.nowDb());
            statement.setLong(3, courierId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Nie można zmienić pojazdu kuriera.", e);
        }
    }

    public void updateSchedule(long courierId, String shiftStart, String shiftEnd, boolean testModeEnabled) {
        String sql = "UPDATE couriers SET shift_start = ?, shift_end = ?, test_mode_enabled = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, defaultShift(shiftStart, "06:00"));
            statement.setString(2, defaultShift(shiftEnd, "14:00"));
            statement.setInt(3, testModeEnabled ? 1 : 0);
            statement.setString(4, TimeUtil.nowDb());
            statement.setLong(5, courierId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Nie można zmienić grafiku kuriera.", e);
        }
    }

    public void updateLocation(long courierId, long mapPointId, Double latitude, Double longitude,
                               String locationLabel, boolean recordHistory) {
        String now = TimeUtil.nowDb();
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE couriers
                    SET current_map_point_id = ?,
                        current_latitude = ?,
                        current_longitude = ?,
                        current_location_label = ?,
                        updated_at = ?
                    WHERE id = ?
                    """)) {
                update.setLong(1, mapPointId);
                setNullableDouble(update, 2, latitude);
                setNullableDouble(update, 3, longitude);
                update.setString(4, locationLabel);
                update.setString(5, now);
                update.setLong(6, courierId);
                update.executeUpdate();
            }
            if (recordHistory) {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO courier_locations(courier_id, map_point_id, latitude, longitude, location_label, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    insert.setLong(1, courierId);
                    insert.setLong(2, mapPointId);
                    setNullableDouble(insert, 3, latitude);
                    setNullableDouble(insert, 4, longitude);
                    insert.setString(5, locationLabel);
                    insert.setString(6, now);
                    insert.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można zmienić lokalizacji kuriera.", e);
        }
    }

    public void deleteByUserId(long userId) {
        try (Connection connection = databaseManager.getConnection()) {
            Long courierId = null;
            try (PreparedStatement find = connection.prepareStatement("SELECT id FROM couriers WHERE user_id = ?")) {
                find.setLong(1, userId);
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        courierId = rs.getLong("id");
                    }
                }
            }
            if (courierId == null) {
                return;
            }
            String now = TimeUtil.nowDb();
            try (PreparedStatement parcels = connection.prepareStatement("""
                    UPDATE parcels
                    SET assigned_courier_id = NULL,
                        status = CASE
                            WHEN status IN ('DELIVERED', 'CANCELED', 'DELIVERY_PROBLEM') THEN status
                            ELSE 'WAITING_FOR_COURIER'
                        END,
                        updated_at = ?
                    WHERE assigned_courier_id = ?
                    """)) {
                parcels.setString(1, now);
                parcels.setLong(2, courierId);
                parcels.executeUpdate();
            }
            try (PreparedStatement locations = connection.prepareStatement("DELETE FROM courier_locations WHERE courier_id = ?")) {
                locations.setLong(1, courierId);
                locations.executeUpdate();
            }
            try (PreparedStatement courier = connection.prepareStatement("DELETE FROM couriers WHERE id = ?")) {
                courier.setLong(1, courierId);
                courier.executeUpdate();
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można usunąć profilu kuriera.", e);
        }
    }

    private Optional<Courier> findSingle(String where, long value) {
        List<Courier> couriers = findMany(where, value);
        return couriers.isEmpty() ? Optional.empty() : Optional.of(couriers.getFirst());
    }

    private List<Courier> findMany(String where, Object... params) {
        String sql = """
                SELECT c.*, u.first_name, u.last_name, u.email, u.phone,
                       COALESCE(NULLIF(c.current_location_label, ''), mp.name) AS point_name
                FROM couriers c
                JOIN users u ON u.id = c.user_id
                JOIN map_points mp ON mp.id = c.current_map_point_id
                """ + " " + where + " ORDER BY u.last_name, u.first_name";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Courier> couriers = new ArrayList<>();
                while (rs.next()) {
                    couriers.add(map(rs));
                }
                return couriers;
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać kurierów.", e);
        }
    }

    private Courier map(ResultSet rs) throws SQLException {
        return new Courier(
                rs.getLong("id"),
                rs.getLong("user_id"),
                CourierStatus.valueOf(rs.getString("status")),
                rs.getLong("current_map_point_id"),
                rs.getString("vehicle_number"),
                Db.dateTime(rs, "updated_at"),
                (rs.getString("first_name") + " " + rs.getString("last_name")).trim(),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("point_name"),
                nullableDouble(rs, "current_latitude"),
                nullableDouble(rs, "current_longitude"),
                rs.getString("shift_start"),
                rs.getString("shift_end"),
                rs.getInt("test_mode_enabled") == 1
        );
    }

    private String defaultShift(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setDouble(index, value);
        }
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
