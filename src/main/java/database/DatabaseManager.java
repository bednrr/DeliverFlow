package database;

import util.AppConfig;
import util.ServiceException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public DatabaseManager(AppConfig config) {
        if (config.mysqlCreateDatabase()) {
            ensureMySqlDatabase(config);
        }
        this.jdbcUrl = config.mysqlJdbcUrl();
        this.username = config.mysqlUser();
        this.password = config.mysqlPassword();
    }

    public String fullNameExpression(String firstNameExpression, String lastNameExpression) {
        return "CONCAT(" + firstNameExpression + ", ' ', " + lastNameExpression + ")";
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET NAMES utf8mb4");
        }
        return connection;
    }

    public void initializeSchema() {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        first_name VARCHAR(255) NOT NULL,
                        last_name VARCHAR(255) NOT NULL,
                        email VARCHAR(255) NOT NULL UNIQUE,
                        phone VARCHAR(255) NOT NULL,
                        password VARCHAR(255) NOT NULL,
                        role VARCHAR(255) NOT NULL,
                        theme VARCHAR(255) NOT NULL DEFAULT 'LIGHT',
                        blocked TINYINT(1) NOT NULL DEFAULT 0,
                        created_at VARCHAR(32) NOT NULL,
                        updated_at VARCHAR(32) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS map_points (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        name VARCHAR(255) NOT NULL UNIQUE,
                        latitude DOUBLE NOT NULL,
                        longitude DOUBLE NOT NULL,
                        warehouse TINYINT(1) NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS client_addresses (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        user_id BIGINT NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        address_text TEXT NOT NULL,
                        map_point_id BIGINT NOT NULL,
                        notes TEXT,
                        created_at VARCHAR(32) NOT NULL,
                        updated_at VARCHAR(32) NOT NULL,
                        FOREIGN KEY(user_id) REFERENCES users(id),
                        FOREIGN KEY(map_point_id) REFERENCES map_points(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS couriers (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        user_id BIGINT NOT NULL UNIQUE,
                        status VARCHAR(255) NOT NULL,
                        current_map_point_id BIGINT NOT NULL,
                        vehicle_number VARCHAR(255) NOT NULL,
                        updated_at VARCHAR(32) NOT NULL,
                        FOREIGN KEY(user_id) REFERENCES users(id),
                        FOREIGN KEY(current_map_point_id) REFERENCES map_points(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS parcels (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        title VARCHAR(255) NOT NULL,
                        description TEXT,
                        size VARCHAR(255) NOT NULL,
                        weight_kg DOUBLE NOT NULL,
                        sender_user_id BIGINT NOT NULL,
                        receiver_name VARCHAR(255) NOT NULL,
                        receiver_phone VARCHAR(255) NOT NULL,
                        sender_address_text TEXT NOT NULL,
                        sender_map_point_id BIGINT NOT NULL,
                        receiver_address_text TEXT NOT NULL,
                        receiver_map_point_id BIGINT NOT NULL,
                        status VARCHAR(255) NOT NULL,
                        assigned_courier_id BIGINT,
                        created_at VARCHAR(32) NOT NULL,
                        updated_at VARCHAR(32) NOT NULL,
                        estimated_price VARCHAR(255) NOT NULL,
                        FOREIGN KEY(sender_user_id) REFERENCES users(id),
                        FOREIGN KEY(sender_map_point_id) REFERENCES map_points(id),
                        FOREIGN KEY(receiver_map_point_id) REFERENCES map_points(id),
                        FOREIGN KEY(assigned_courier_id) REFERENCES couriers(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS parcel_status_history (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        parcel_id BIGINT NOT NULL,
                        status VARCHAR(255) NOT NULL,
                        note TEXT,
                        created_at VARCHAR(32) NOT NULL,
                        FOREIGN KEY(parcel_id) REFERENCES parcels(id)
                    )
                    """);
            statement.execute("UPDATE parcels SET status = 'WAITING_FOR_COURIER' WHERE status IN ('CREATED', 'COURIER_TO_SENDER')");
            statement.execute("UPDATE parcels SET status = 'IN_TRANSIT' WHERE status = 'TO_WAREHOUSE'");
            statement.execute("UPDATE parcels SET status = 'PICKUP_IN_PROGRESS' WHERE status = 'PICKED_UP'");
            statement.execute("UPDATE parcels SET status = 'PICKUP_IN_PROGRESS' WHERE status = 'IN_WAREHOUSE'");
            statement.execute("""
                    UPDATE parcel_status_history
                    SET status = 'WAITING_FOR_COURIER'
                    WHERE status IN ('CREATED', 'COURIER_TO_SENDER')
                    """);
            statement.execute("""
                    UPDATE parcel_status_history
                    SET status = 'IN_TRANSIT'
                    WHERE status = 'TO_WAREHOUSE'
                    """);
            statement.execute("""
                    UPDATE parcel_status_history
                    SET status = 'PICKUP_IN_PROGRESS'
                    WHERE status = 'PICKED_UP'
                    """);
            statement.execute("""
                    UPDATE parcel_status_history
                    SET status = 'PICKUP_IN_PROGRESS'
                    WHERE status = 'IN_WAREHOUSE'
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS courier_locations (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        courier_id BIGINT NOT NULL,
                        map_point_id BIGINT NOT NULL,
                        created_at VARCHAR(32) NOT NULL,
                        FOREIGN KEY(courier_id) REFERENCES couriers(id),
                        FOREIGN KEY(map_point_id) REFERENCES map_points(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS event_logs (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        level VARCHAR(255) NOT NULL,
                        message TEXT NOT NULL,
                        created_at VARCHAR(32) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        sender_user_id BIGINT NOT NULL,
                        recipient_user_id BIGINT NOT NULL,
                        title VARCHAR(255) NOT NULL,
                        message TEXT NOT NULL,
                        created_at VARCHAR(32) NOT NULL,
                        read_at VARCHAR(32),
                        FOREIGN KEY(sender_user_id) REFERENCES users(id),
                        FOREIGN KEY(recipient_user_id) REFERENCES users(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        `key` VARCHAR(255) PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            addColumnIfMissing(connection, "couriers", "current_latitude", "DOUBLE");
            addColumnIfMissing(connection, "couriers", "current_longitude", "DOUBLE");
            addColumnIfMissing(connection, "couriers", "current_location_label", "TEXT");
            addColumnIfMissing(connection, "couriers", "shift_start", "VARCHAR(255)");
            addColumnIfMissing(connection, "couriers", "shift_end", "VARCHAR(255)");
            addColumnIfMissing(connection, "couriers", "test_mode_enabled", "TINYINT(1) NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "courier_locations", "latitude", "DOUBLE");
            addColumnIfMissing(connection, "courier_locations", "longitude", "DOUBLE");
            addColumnIfMissing(connection, "courier_locations", "location_label", "TEXT");
            migrateMapPointCoordinates(connection);
            dropTableIfExists(connection, "map_edges");
            migrateWarehouseToCentral(connection);
            backfillCourierShifts(connection);
            ensureDemoTestCouriers(connection);
            migrateSeedPointsToRealCoordinates(connection);
            realignCentralCouriers(connection);
            backfillCourierCoordinates(connection);
        } catch (SQLException e) {
            throw new ServiceException("Nie mozna przygotowac bazy danych.", e);
        }
    }

    private void addColumnIfMissing(Connection connection, String tableName, String columnName, String definition)
            throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
            return rs.next();
        }
    }

    private void dropColumnIfExists(Connection connection, String tableName, String columnName) throws SQLException {
        if (!columnExists(connection, tableName, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
        }
    }

    private void dropTableIfExists(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + tableName);
        }
    }

    private void migrateMapPointCoordinates(Connection connection) throws SQLException {
        boolean hasX = columnExists(connection, "map_points", "x");
        boolean hasY = columnExists(connection, "map_points", "y");
        addColumnIfMissing(connection, "map_points", "latitude", "DOUBLE");
        addColumnIfMissing(connection, "map_points", "longitude", "DOUBLE");
        try (Statement statement = connection.createStatement()) {
            if (hasX) {
                statement.execute("UPDATE map_points SET latitude = COALESCE(latitude, x)");
            }
            if (hasY) {
                statement.execute("UPDATE map_points SET longitude = COALESCE(longitude, y)");
            }
            statement.execute("UPDATE map_points SET latitude = 0 WHERE latitude IS NULL");
            statement.execute("UPDATE map_points SET longitude = 0 WHERE longitude IS NULL");
            statement.execute("ALTER TABLE map_points MODIFY COLUMN latitude DOUBLE NOT NULL");
            statement.execute("ALTER TABLE map_points MODIFY COLUMN longitude DOUBLE NOT NULL");
        }
        dropColumnIfExists(connection, "map_points", "x");
        dropColumnIfExists(connection, "map_points", "y");
    }

    private void backfillCourierCoordinates(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE couriers
                    SET current_latitude = COALESCE(
                            current_latitude,
                            (SELECT mp.latitude FROM map_points mp WHERE mp.id = couriers.current_map_point_id)
                        ),
                        current_longitude = COALESCE(
                            current_longitude,
                            (SELECT mp.longitude FROM map_points mp WHERE mp.id = couriers.current_map_point_id)
                        ),
                        current_location_label = COALESCE(
                            NULLIF(current_location_label, ''),
                            (SELECT mp.name FROM map_points mp WHERE mp.id = couriers.current_map_point_id)
                        )
                    """);
        }
    }

    private void backfillCourierShifts(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE couriers
                    SET shift_start = COALESCE(shift_start, CASE WHEN id % 2 = 1 THEN '06:00' ELSE '14:00' END),
                        shift_end = COALESCE(shift_end, CASE WHEN id % 2 = 1 THEN '14:00' ELSE '22:00' END),
                        test_mode_enabled = COALESCE(test_mode_enabled, 0)
                    """);
        }
    }

    private void ensureDemoTestCouriers(Connection connection) throws SQLException {
        try (PreparedStatement check = connection.prepareStatement(
                "SELECT 1 FROM couriers WHERE test_mode_enabled = 1 LIMIT 1");
             ResultSet rs = check.executeQuery()) {
            if (rs.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE couriers
                    SET test_mode_enabled = 1
                    WHERE user_id IN (
                        SELECT id
                        FROM users
                        WHERE email IN ('kurier14@deliverflow.local', 'kurier15@deliverflow.local')
                    )
                    """);
        }
    }

    private void migrateSeedPointsToRealCoordinates(Connection connection) throws SQLException {
        updatePoint(connection, "Rynek Glowny", 50.06178, 19.93737, false);
        updatePoint(connection, "Rynek Główny", 50.06178, 19.93737, false);
        updatePoint(connection, "Dworzec Glowny", 50.06765, 19.94719, false);
        updatePoint(connection, "Dworzec Główny", 50.06765, 19.94719, false);
        updatePoint(connection, "Kazimierz", 50.04958, 19.94443, false);
        updatePoint(connection, "Podgorze", 50.04073, 19.95791, false);
        updatePoint(connection, "Podgórze", 50.04073, 19.95791, false);
        updatePoint(connection, "Nowa Huta", 50.07295, 20.03791, false);
        updatePoint(connection, "Ruczaj", 50.01531, 19.89979, false);
        updatePoint(connection, "Bronowice", 50.08240, 19.88382, false);
        updatePoint(connection, "Kurdwanow", 50.02033, 19.95859, false);
        updatePoint(connection, "Kurdwanów", 50.02033, 19.95859, false);
        updatePoint(connection, "Pradnik Bialy", 50.09445, 19.92198, false);
        updatePoint(connection, "Prądnik Biały", 50.09445, 19.92198, false);
        updatePoint(connection, "Biezanow", 50.01542, 20.02893, false);
        updatePoint(connection, "Bieżanów", 50.01542, 20.02893, false);
        updatePoint(connection, "Zablocie", 50.04967, 19.96018, false);
        updatePoint(connection, "Zabłocie", 50.04967, 19.96018, false);
        updatePoint(connection, "Lagiewniki", 50.02288, 19.93624, false);
        updatePoint(connection, "Łagiewniki", 50.02288, 19.93624, false);
        updatePoint(connection, "Krowodrza", 50.07531, 19.92357, false);
        updatePoint(connection, "Mistrzejowice", 50.09766, 20.00074, false);
        updatePoint(connection, "Centrala", 50.072427, 19.943229, true);
    }

    private void migrateWarehouseToCentral(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE map_points
                    SET name = 'Centrala'
                    WHERE name = 'Magazyn Centralny'
                    AND NOT EXISTS (
                        SELECT 1 FROM map_points existing WHERE existing.name = 'Centrala'
                    )
                    """);
            statement.execute("""
                    UPDATE map_points
                    SET warehouse = CASE WHEN name = 'Centrala' THEN 1 ELSE 0 END
                    WHERE name IN ('Centrala', 'Magazyn Centralny')
                    """);
        }
    }

    private void realignCentralCouriers(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE couriers
                    SET current_latitude = (SELECT mp.latitude FROM map_points mp WHERE mp.id = couriers.current_map_point_id),
                        current_longitude = (SELECT mp.longitude FROM map_points mp WHERE mp.id = couriers.current_map_point_id),
                        current_location_label = 'Centrala'
                    WHERE current_map_point_id = (
                            SELECT id FROM map_points WHERE name = 'Centrala' LIMIT 1
                        )
                        AND (
                            current_location_label IS NULL
                            OR TRIM(current_location_label) = ''
                            OR current_location_label IN ('Magazyn Centralny', 'Centrala')
                        )
                    """);
        }
    }

    private void updatePoint(Connection connection, String name, double latitude, double longitude, boolean warehouse)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE map_points
                SET latitude = ?, longitude = ?, warehouse = ?
                WHERE name = ?
                """)) {
            statement.setDouble(1, latitude);
            statement.setDouble(2, longitude);
            statement.setInt(3, warehouse ? 1 : 0);
            statement.setString(4, name);
            statement.executeUpdate();
        }
    }

    private static void ensureMySqlDatabase(AppConfig config) {
        String databaseName = config.mysqlDatabase();
        if (!databaseName.matches("[A-Za-z0-9_]+")) {
            throw new ServiceException("Nazwa bazy MySQL moze zawierac tylko litery, cyfry i podkreslenia.");
        }
        try (Connection connection = DriverManager.getConnection(
                config.mysqlServerJdbcUrl(), config.mysqlUser(), config.mysqlPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException e) {
            throw new ServiceException("Nie mozna przygotowac bazy MySQL.", e);
        }
    }
}
