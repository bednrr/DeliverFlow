package client.map;

import protocol.responses.FleetTaskPhase;

import java.util.List;
import java.util.Objects;

public final class RoutePreviewPolicy {
    public static final double DEFAULT_DEVIATION_THRESHOLD_METERS = 50.0;

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private RoutePreviewPolicy() {
    }

    public static boolean shouldClearRoute(FleetTaskPhase phase, Long targetMapPointId) {
        return phase == null
                || phase == FleetTaskPhase.IDLE
                || phase == FleetTaskPhase.PICKUP_COOLDOWN
                || targetMapPointId == null;
    }

    public static boolean shouldFetchRoute(String previousScopeKey, String currentScopeKey,
                                           List<GeoCoordinate> routeGeometry, GeoCoordinate courierPosition) {
        if (!Objects.equals(previousScopeKey, currentScopeKey)) {
            return true;
        }
        if (routeGeometry == null || routeGeometry.isEmpty()) {
            return true;
        }
        return distanceFromRouteMeters(courierPosition, routeGeometry) > DEFAULT_DEVIATION_THRESHOLD_METERS;
    }

    public static double distanceFromRouteMeters(GeoCoordinate point, List<GeoCoordinate> routeGeometry) {
        if (point == null || routeGeometry == null || routeGeometry.isEmpty()) {
            return Double.MAX_VALUE;
        }
        if (routeGeometry.size() == 1) {
            return distanceMeters(point, routeGeometry.getFirst());
        }
        double best = Double.MAX_VALUE;
        for (int i = 1; i < routeGeometry.size(); i++) {
            best = Math.min(best, distanceToSegmentMeters(point, routeGeometry.get(i - 1), routeGeometry.get(i)));
        }
        return best;
    }

    private static double distanceToSegmentMeters(GeoCoordinate point, GeoCoordinate start, GeoCoordinate end) {
        double referenceLat = Math.toRadians((start.latitude() + end.latitude() + point.latitude()) / 3.0);
        double px = metersX(point.longitude(), referenceLat);
        double py = metersY(point.latitude());
        double sx = metersX(start.longitude(), referenceLat);
        double sy = metersY(start.latitude());
        double ex = metersX(end.longitude(), referenceLat);
        double ey = metersY(end.latitude());
        double dx = ex - sx;
        double dy = ey - sy;
        double segmentLengthSquared = dx * dx + dy * dy;
        if (segmentLengthSquared <= 0.001) {
            return distanceMeters(point, start);
        }
        double t = ((px - sx) * dx + (py - sy) * dy) / segmentLengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = sx + t * dx;
        double closestY = sy + t * dy;
        double diffX = px - closestX;
        double diffY = py - closestY;
        return Math.sqrt(diffX * diffX + diffY * diffY);
    }

    private static double metersX(double longitude, double referenceLatRadians) {
        return Math.toRadians(longitude) * EARTH_RADIUS_METERS * Math.cos(referenceLatRadians);
    }

    private static double metersY(double latitude) {
        return Math.toRadians(latitude) * EARTH_RADIUS_METERS;
    }

    private static double distanceMeters(GeoCoordinate first, GeoCoordinate second) {
        double deltaLat = Math.toRadians(second.latitude() - first.latitude());
        double deltaLon = Math.toRadians(second.longitude() - first.longitude());
        double startLat = Math.toRadians(first.latitude());
        double endLat = Math.toRadians(second.latitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(startLat) * Math.cos(endLat)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
