package service;

import client.map.GeoCoordinate;
import client.map.GeocodingService;
import client.map.OsrmRoute;
import client.map.OsrmRouteService;
import model.MapPoint;
import model.RouteResult;
import repository.MapRepository;
import util.AppConfig;
import util.ValidationException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MapService {
    private static final double FALLBACK_ROUTING_SPEED_KMH = 35.0;

    private final MapRepository mapRepository;
    private final AppConfig config;
    private final GeocodingService geocodingService;
    private final OsrmRouteService osrmRouteService;
    private final Map<String, OsrmRoute> routeCache = new ConcurrentHashMap<>();

    public MapService(MapRepository mapRepository, AppConfig config) {
        this(mapRepository, config, new GeocodingService(config), new OsrmRouteService(config));
    }

    MapService(MapRepository mapRepository, AppConfig config,
               GeocodingService geocodingService, OsrmRouteService osrmRouteService) {
        this.mapRepository = mapRepository;
        this.config = config;
        this.geocodingService = geocodingService;
        this.osrmRouteService = osrmRouteService;
    }

    public List<MapPoint> listPoints() {
        return mapRepository.findAllPoints();
    }

    public MapPoint warehouse() {
        return mapRepository.findWarehouse()
                .orElseThrow(() -> new ValidationException("Nie skonfigurowano centrali kurierów."));
    }

    public MapPoint central() {
        return warehouse();
    }

    public Optional<MapPoint> pointByName(String name) {
        return mapRepository.findPointByName(name);
    }

    public MapPoint requirePoint(long id) {
        return mapRepository.findById(id)
                .orElseThrow(() -> new ValidationException("Punkt mapy nie istnieje."));
    }

    public MapPoint resolveOrCreatePoint(String addressText, long requestedPointId) {
        if (requestedPointId > 0) {
            return requirePoint(requestedPointId);
        }
        String normalizedAddress = normalizeAddress(addressText);
        return mapRepository.findPointByName(normalizedAddress)
                .orElseGet(() -> {
                    if (!config.onlineMapRemoteEnabled()) {
                        throw new ValidationException("Brak współrzędnych dla adresu: " + normalizedAddress + ".");
                    }
                    GeoCoordinate coordinate = geocodeAddress(normalizedAddress);
                    MapPoint point = new MapPoint();
                    point.setName(normalizedAddress);
                    point.setLatitude(coordinate.latitude());
                    point.setLongitude(coordinate.longitude());
                    point.setWarehouse(false);
                    return mapRepository.savePoint(point);
                });
    }

    public RouteResult shortestRoute(long fromPointId, long toPointId) {
        MapPoint from = requirePoint(fromPointId);
        MapPoint to = requirePoint(toPointId);
        if (fromPointId == toPointId) {
            return new RouteResult(List.of(from), 0, 0);
        }
        OsrmRoute route = routeBetween(from, to);
        return summarize(route, from, to);
    }

    public RouteResult shortestRoute(GeoCoordinate from, GeoCoordinate to, String fromLabel, String toLabel) {
        if (sameCoordinate(from, to)) {
            return new RouteResult(List.of(pointLabel(fromLabel, from)), 0, 0);
        }
        OsrmRoute route = routeBetween(from, to);
        return summarize(route, pointLabel(fromLabel, from), pointLabel(toLabel, to));
    }

    private OsrmRoute routeBetween(MapPoint from, MapPoint to) {
        return routeBetween(coordinateOf(from), coordinateOf(to));
    }

    public OsrmRoute routeBetween(GeoCoordinate from, GeoCoordinate to) {
        if (sameCoordinate(from, to)) {
            return new OsrmRoute(List.of(from), 0, 0);
        }
        String cacheKey = cacheKey(from, to);
        return routeCache.computeIfAbsent(cacheKey, ignored -> loadRoute(from, to));
    }

    public GeoCoordinate coordinateOf(MapPoint point) {
        return new GeoCoordinate(point.getLatitude(), point.getLongitude());
    }

    private GeoCoordinate geocodeAddress(String addressText) {
        return geocodingService.geocode(addressQuery(addressText));
    }

    private OsrmRoute loadRoute(GeoCoordinate from, GeoCoordinate to) {
        if (config.onlineMapRemoteEnabled()) {
            try {
                return osrmRouteService.fetchRoute(from.latitude(), from.longitude(), to.latitude(), to.longitude());
            } catch (ValidationException ignored) {
                // Public OSRM may be temporarily unavailable; fall back to a straight-line route.
            }
        }
        double distanceKm = haversineDistanceKm(from, to);
        double durationSeconds = distanceKm == 0 ? 0 : distanceKm / FALLBACK_ROUTING_SPEED_KMH * 3600.0;
        return new OsrmRoute(List.of(from, to), durationSeconds, distanceKm * 1000.0);
    }

    private RouteResult summarize(OsrmRoute route, MapPoint from, MapPoint to) {
        int minutes = (int) Math.round(route.durationSeconds() / 60.0);
        return new RouteResult(List.of(from, to), Math.max(minutes, 0), route.distanceMeters() / 1000.0);
    }

    private MapPoint pointLabel(String label, GeoCoordinate coordinate) {
        MapPoint point = new MapPoint();
        point.setName(label == null || label.isBlank() ? "Punkt" : label);
        point.setLatitude(coordinate.latitude());
        point.setLongitude(coordinate.longitude());
        return point;
    }

    private String addressQuery(String addressText) {
        String normalized = normalizeAddress(addressText);
        if (normalized.toLowerCase(Locale.ROOT).contains("krak")) {
            return normalized;
        }
        return normalized + ", " + config.onlineMapLocationBias();
    }

    private String normalizeAddress(String addressText) {
        if (addressText == null) {
            throw new ValidationException("Adres jest wymagany.");
        }
        String normalized = addressText.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new ValidationException("Adres jest wymagany.");
        }
        return normalized;
    }

    private String cacheKey(GeoCoordinate from, GeoCoordinate to) {
        return String.format(Locale.US, "%.6f,%.6f->%.6f,%.6f",
                from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    private boolean sameCoordinate(GeoCoordinate from, GeoCoordinate to) {
        return Math.abs(from.latitude() - to.latitude()) < 0.000001
                && Math.abs(from.longitude() - to.longitude()) < 0.000001;
    }

    private double haversineDistanceKm(GeoCoordinate from, GeoCoordinate to) {
        double earthRadiusKm = 6371.0088;
        double deltaLat = Math.toRadians(to.latitude() - from.latitude());
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());
        double startLat = Math.toRadians(from.latitude());
        double endLat = Math.toRadians(to.latitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(startLat) * Math.cos(endLat)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
