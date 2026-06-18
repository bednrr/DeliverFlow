package repository;

import database.DatabaseManager;
import model.Parcel;
import model.ParcelSize;
import model.ParcelStatus;
import model.ParcelStatusHistory;
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

public class ParcelRepository {
    private final DatabaseManager databaseManager;

    public ParcelRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Parcel save(Parcel parcel) {
        String sql = """
                INSERT INTO parcels(title, description, size, weight_kg, sender_user_id, receiver_name, receiver_phone,
                sender_address_text, sender_map_point_id, receiver_address_text, receiver_map_point_id, status,
                assigned_courier_id, created_at, updated_at, estimated_price)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String now = TimeUtil.nowDb();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, parcel.getTitle());
            statement.setString(2, parcel.getDescription());
            statement.setString(3, parcel.getSize().name());
            statement.setDouble(4, parcel.getWeightKg());
            statement.setLong(5, parcel.getSenderUserId());
            statement.setString(6, parcel.getReceiverName());
            statement.setString(7, parcel.getReceiverPhone());
            statement.setString(8, parcel.getSenderAddressText());
            statement.setLong(9, parcel.getSenderMapPointId());
            statement.setString(10, parcel.getReceiverAddressText());
            statement.setLong(11, parcel.getReceiverMapPointId());
            statement.setString(12, parcel.getStatus().name());
            if (parcel.getAssignedCourierId() == null) {
                statement.setObject(13, null);
            } else {
                statement.setLong(13, parcel.getAssignedCourierId());
            }
            statement.setString(14, now);
            statement.setString(15, now);
            statement.setString(16, parcel.getEstimatedPrice().toPlainString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    parcel.setId(keys.getLong(1));
                }
            }
            addHistory(parcel.getId(), parcel.getStatus(), "Utworzono paczkę.");
            return findById(parcel.getId()).orElseThrow();
        } catch (SQLException e) {
            throw new ServiceException("Nie można zapisać paczki.", e);
        }
    }

    public Optional<Parcel> findById(long id) {
        List<Parcel> result = findMany("WHERE p.id = ?", id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    public List<Parcel> findAll() {
        return findMany("");
    }

    public List<Parcel> findBySender(long senderUserId) {
        return findMany("WHERE p.sender_user_id = ?", senderUserId);
    }

    public List<Parcel> findByCourier(long courierId) {
        return findMany("WHERE p.assigned_courier_id = ?", courierId);
    }

    public List<Parcel> findWaiting() {
        return findMany("""
                        WHERE p.status IN ('WAITING_FOR_COURIER', 'WAREHOUSE')
                        AND p.assigned_courier_id IS NULL
                        """,
                """
                ORDER BY CASE p.status
                    WHEN 'WAITING_FOR_COURIER' THEN 0
                    ELSE 1
                END, p.created_at, p.id
                """);
    }

    public List<Parcel> findActiveAssignedToCourier(long courierId) {
        return findMany("""
                WHERE p.assigned_courier_id = ?
                AND p.status NOT IN ('DELIVERED', 'CANCELED', 'DELIVERY_PROBLEM')
                """, "ORDER BY p.updated_at DESC, p.id DESC", courierId);
    }

    public void updateAssignmentAndStatus(long parcelId, long courierId, ParcelStatus status, String note) {
        String sql = """
                UPDATE parcels
                SET assigned_courier_id = ?, status = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courierId);
            statement.setString(2, status.name());
            statement.setString(3, TimeUtil.nowDb());
            statement.setLong(4, parcelId);
            statement.executeUpdate();
            addHistory(parcelId, status, note);
        } catch (SQLException e) {
            throw new ServiceException("Nie można przypisać kuriera do paczki.", e);
        }
    }

    public void updateStatus(long parcelId, ParcelStatus status, String note) {
        String sql = """
                UPDATE parcels
                SET status = ?,
                    updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setString(2, TimeUtil.nowDb());
            statement.setLong(3, parcelId);
            statement.executeUpdate();
            addHistory(parcelId, status, note);
        } catch (SQLException e) {
            throw new ServiceException("Nie można zmienić statusu paczki.", e);
        }
    }

    public void clearAssignmentAndStatus(long parcelId, ParcelStatus status, String note) {
        String sql = """
                UPDATE parcels
                SET assigned_courier_id = NULL,
                    status = ?,
                    updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setString(2, TimeUtil.nowDb());
            statement.setLong(3, parcelId);
            statement.executeUpdate();
            addHistory(parcelId, status, note);
        } catch (SQLException e) {
            throw new ServiceException("Nie można przywrócić paczki do oczekiwania na kuriera.", e);
        }
    }

    public void addHistory(long parcelId, ParcelStatus status, String note) {
        String sql = "INSERT INTO parcel_status_history(parcel_id, status, note, created_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parcelId);
            statement.setString(2, status.name());
            statement.setString(3, note);
            statement.setString(4, TimeUtil.nowDb());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Nie można zapisać historii paczki.", e);
        }
    }

    public List<ParcelStatusHistory> history(long parcelId) {
        String sql = "SELECT * FROM parcel_status_history WHERE parcel_id = ? ORDER BY created_at";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parcelId);
            try (ResultSet rs = statement.executeQuery()) {
                List<ParcelStatusHistory> history = new ArrayList<>();
                while (rs.next()) {
                    history.add(new ParcelStatusHistory(
                            rs.getLong("id"),
                            rs.getLong("parcel_id"),
                            ParcelStatus.fromDatabase(rs.getString("status")),
                            rs.getString("note"),
                            Db.dateTime(rs, "created_at")
                    ));
                }
                return history;
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać historii paczki.", e);
        }
    }

    private List<Parcel> findMany(String where, Object... params) {
        return findMany(where, "ORDER BY p.updated_at DESC, p.id DESC", params);
    }

    private List<Parcel> findMany(String where, String orderBy, Object... params) {
        String senderName = databaseManager.fullNameExpression("sender.first_name", "sender.last_name");
        String courierName = databaseManager.fullNameExpression("courier_user.first_name", "courier_user.last_name");
        String sql = """
                SELECT p.*, %s AS sender_name,
                       sender.phone AS sender_phone,
                       sender_point.name AS sender_point_name,
                       receiver_point.name AS receiver_point_name,
                       %s AS courier_name,
                       courier_user.phone AS courier_phone
                FROM parcels p
                JOIN users sender ON sender.id = p.sender_user_id
                JOIN map_points sender_point ON sender_point.id = p.sender_map_point_id
                JOIN map_points receiver_point ON receiver_point.id = p.receiver_map_point_id
                LEFT JOIN couriers courier ON courier.id = p.assigned_courier_id
                LEFT JOIN users courier_user ON courier_user.id = courier.user_id
                """.formatted(senderName, courierName) + " " + where + " " + orderBy;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Parcel> parcels = new ArrayList<>();
                while (rs.next()) {
                    parcels.add(map(rs));
                }
                return parcels;
            }
        } catch (SQLException e) {
            throw new ServiceException("Nie można odczytać paczek.", e);
        }
    }

    private Parcel map(ResultSet rs) throws SQLException {
        Parcel parcel = new Parcel();
        parcel.setId(rs.getLong("id"));
        parcel.setTitle(rs.getString("title"));
        parcel.setDescription(rs.getString("description"));
        parcel.setSize(ParcelSize.valueOf(rs.getString("size")));
        parcel.setWeightKg(rs.getDouble("weight_kg"));
        parcel.setSenderUserId(rs.getLong("sender_user_id"));
        parcel.setSenderName(rs.getString("sender_name"));
        parcel.setSenderPhone(rs.getString("sender_phone"));
        parcel.setReceiverName(rs.getString("receiver_name"));
        parcel.setReceiverPhone(rs.getString("receiver_phone"));
        parcel.setSenderAddressText(rs.getString("sender_address_text"));
        parcel.setSenderMapPointId(rs.getLong("sender_map_point_id"));
        parcel.setSenderMapPointName(rs.getString("sender_point_name"));
        parcel.setReceiverAddressText(rs.getString("receiver_address_text"));
        parcel.setReceiverMapPointId(rs.getLong("receiver_map_point_id"));
        parcel.setReceiverMapPointName(rs.getString("receiver_point_name"));
        parcel.setStatus(ParcelStatus.fromDatabase(rs.getString("status")));
        long courierId = rs.getLong("assigned_courier_id");
        parcel.setAssignedCourierId(rs.wasNull() ? null : courierId);
        parcel.setAssignedCourierName(rs.getString("courier_name"));
        parcel.setAssignedCourierPhone(rs.getString("courier_phone"));
        parcel.setCreatedAt(Db.dateTime(rs, "created_at"));
        parcel.setUpdatedAt(Db.dateTime(rs, "updated_at"));
        parcel.setEstimatedPrice(Db.decimal(rs, "estimated_price"));
        return parcel;
    }
}
