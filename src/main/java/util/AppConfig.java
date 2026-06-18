package util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

public class AppConfig {
    private final Properties properties = new Properties();

    public AppConfig() {
        try (InputStream inputStream = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Nie można odczytać konfiguracji aplikacji.", e);
        }
    }

    public String serverHost() {
        return properties.getProperty("server.host", "127.0.0.1");
    }

    public int serverPort() {
        return intValue("server.port", 5050);
    }

    public String mysqlHost() {
        return properties.getProperty("database.mysql.host", "127.0.0.1");
    }

    public int mysqlPort() {
        return intValue("database.mysql.port", 3306);
    }

    public String mysqlDatabase() {
        return properties.getProperty("database.mysql.name", "deliverflow");
    }

    public String mysqlUser() {
        return properties.getProperty("database.mysql.user", "root");
    }

    public String mysqlPassword() {
        return properties.getProperty("database.mysql.password", "");
    }

    public boolean mysqlCreateDatabase() {
        return Boolean.parseBoolean(properties.getProperty("database.mysql.create-database", "true"));
    }

    public String mysqlJdbcUrl() {
        String configuredUrl = properties.getProperty("database.mysql.url", "").trim();
        if (!configuredUrl.isBlank()) {
            return configuredUrl;
        }
        return "jdbc:mysql://" + mysqlHost() + ":" + mysqlPort() + "/" + mysqlDatabase()
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Europe/Warsaw&allowPublicKeyRetrieval=true&useSSL=false";
    }

    public String mysqlServerJdbcUrl() {
        return "jdbc:mysql://" + mysqlHost() + ":" + mysqlPort()
                + "/?useUnicode=true&characterEncoding=utf8&serverTimezone=Europe/Warsaw&allowPublicKeyRetrieval=true&useSSL=false";
    }

    public Path reportsPath() {
        return Path.of(properties.getProperty("reports.path", "reports"));
    }

    public boolean seedEnabled() {
        return Boolean.parseBoolean(properties.getProperty("database.seed.enabled", "true"));
    }

    public int simulationIntervalSeconds() {
        return intValue("simulation.interval.seconds", 3);
    }

    public double simulationCourierSpeedKmh() {
        return doubleValue("simulation.courier.speed.kmh", 50.0);
    }

    public int simulationPickupCooldownSeconds() {
        return intValue("simulation.pickup.cooldown.seconds", 30);
    }

    public boolean courierShiftsEnabled() {
        return Boolean.parseBoolean(properties.getProperty("courier.shifts.enabled", "true"));
    }

    public boolean courierShiftsTestMode() {
        return Boolean.parseBoolean(properties.getProperty("courier.shifts.test-mode", "false"));
    }

    public String courierShiftsClockOverride() {
        return properties.getProperty("courier.shifts.clock.override", "").trim();
    }

    public int serverMaxThreads() {
        return intValue("server.max.threads", 16);
    }

    public String onlineMapNominatimUrl() {
        return properties.getProperty("online.map.nominatim.url",
                "https://nominatim.openstreetmap.org/search");
    }

    public String onlineMapOsrmUrl() {
        return properties.getProperty("online.map.osrm.url",
                "https://router.project-osrm.org/route/v1/driving");
    }

    public String onlineMapUserAgent() {
        return properties.getProperty("online.map.user-agent",
                "DeliverFlowFleetManager/1.0 (desktop JavaFX semester project)");
    }

    public String onlineMapLocationBias() {
        return properties.getProperty("online.map.location.bias", "Krakow, Poland");
    }

    public double onlineMapDefaultCenterLat() {
        return doubleValue("online.map.default-center.lat", 50.06143);
    }

    public double onlineMapDefaultCenterLng() {
        return doubleValue("online.map.default-center.lng", 19.93658);
    }

    public int onlineMapDefaultZoom() {
        return intValue("online.map.default-zoom", 12);
    }

    public int onlineMapRefreshSeconds() {
        return intValue("online.map.refresh.seconds", 2);
    }

    public boolean onlineMapRemoteEnabled() {
        return Boolean.parseBoolean(properties.getProperty("online.map.remote.enabled", "true"));
    }

    private int intValue(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double doubleValue(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
