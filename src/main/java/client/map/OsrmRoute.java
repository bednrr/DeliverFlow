package client.map;

import java.util.List;

public record OsrmRoute(List<GeoCoordinate> geometry, double durationSeconds, double distanceMeters) {
    public OsrmRoute {
        geometry = List.copyOf(geometry);
    }
}
