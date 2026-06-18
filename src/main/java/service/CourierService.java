package service;

import client.map.GeoCoordinate;
import model.Courier;
import model.CourierStatus;
import model.MapPoint;
import model.RouteResult;
import repository.CourierRepository;
import repository.MapRepository;
import util.AppConfig;
import util.ValidationException;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CourierService {
    private final CourierRepository courierRepository;
    private final MapRepository mapRepository;
    private final MapService mapService;
    private final ReportService reportService;
    private final AppConfig config;

    public CourierService(CourierRepository courierRepository, MapRepository mapRepository,
                          MapService mapService, ReportService reportService, AppConfig config) {
        this.courierRepository = courierRepository;
        this.mapRepository = mapRepository;
        this.mapService = mapService;
        this.reportService = reportService;
        this.config = config;
    }

    public Courier createCourier(long userId, long currentMapPointId, String vehicleNumber,
                                 String shiftStart, String shiftEnd, boolean testModeEnabled) {
        MapPoint point = mapRepository.findById(currentMapPointId)
                .orElseThrow(() -> new ValidationException("Punkt startowy kuriera nie istnieje."));
        Courier courier = new Courier();
        courier.setUserId(userId);
        courier.setCurrentMapPointId(currentMapPointId);
        courier.setCurrentLatitude(point.getLatitude());
        courier.setCurrentLongitude(point.getLongitude());
        courier.setCurrentPointName(point.getName());
        courier.setVehicleNumber(vehicleNumber == null || vehicleNumber.isBlank() ? "DF-" + userId : vehicleNumber);
        courier.setShiftStart(normalizeShift(shiftStart, "06:00"));
        courier.setShiftEnd(normalizeShift(shiftEnd, "14:00"));
        courier.setTestModeEnabled(testModeEnabled);
        courier.setStatus(isWithinWorkingWindow(courier) ? CourierStatus.AVAILABLE : CourierStatus.OFFLINE);
        Courier saved = courierRepository.save(courier);
        courierRepository.updateLocation(saved.getId(), currentMapPointId, point.getLatitude(), point.getLongitude(), point.getName(), true);
        return saved;
    }

    public List<Courier> listCouriers() {
        return courierRepository.findAll();
    }

    public void removeCourierProfileForUser(long userId) {
        courierRepository.deleteByUserId(userId);
        reportService.info("Usunięto profil kuriera dla użytkownika #" + userId + ".");
    }

    public Optional<Courier> findByUserId(long userId) {
        return courierRepository.findByUserId(userId);
    }

    public void updateVehicleForUser(long userId, String vehicleNumber) {
        Courier courier = courierRepository.findByUserId(userId)
                .orElseThrow(() -> new ValidationException("Nie znaleziono profilu kuriera."));
        courierRepository.updateVehicle(courier.getId(), vehicleNumber);
        reportService.info("Zmieniono pojazd kuriera " + courier.getFullName() + ".");
    }

    public Courier requireCourier(long courierId) {
        return courierRepository.findById(courierId)
                .orElseThrow(() -> new ValidationException("Nie znaleziono kuriera."));
    }

    public Optional<Courier> findClosestAssignable(long pickupMapPointId) {
        return courierRepository.findAssignable().stream()
                .filter(this::canAcceptAssignments)
                .map(courier -> new CourierDistance(courier, routeFromCourier(courier, pickupMapPointId)))
                .filter(item -> item.route.exists())
                .min(Comparator.comparingInt((CourierDistance item) -> item.courier().getStatus() == CourierStatus.AVAILABLE ? 0 : 1)
                        .thenComparingInt(item -> item.route().getMinutes()))
                .map(CourierDistance::courier);
    }

    public void updateStatus(long courierId, CourierStatus status) {
        if (status == null) {
            throw new ValidationException("Status kuriera jest wymagany.");
        }
        Courier courier = requireCourier(courierId);
        courierRepository.updateStatus(courierId, status);
        reportService.info("Kurier " + courier.getFullName() + " zmienił status na: " + status.displayName() + ".");
    }

    public void updateScheduleForUser(long userId, String shiftStart, String shiftEnd, boolean testModeEnabled) {
        Courier courier = courierRepository.findByUserId(userId)
                .orElseThrow(() -> new ValidationException("Nie znaleziono profilu kuriera."));
        courierRepository.updateSchedule(courier.getId(), normalizeShift(shiftStart, "06:00"),
                normalizeShift(shiftEnd, "14:00"), testModeEnabled);
        reportService.info("Zmieniono grafik kuriera " + courier.getFullName() + ".");
    }

    public boolean canAcceptAssignments(Courier courier) {
        if (courier.getStatus() != CourierStatus.AVAILABLE) {
            return false;
        }
        return isWithinWorkingWindow(courier);
    }

    public boolean isWithinWorkingWindow(Courier courier) {
        if (!usesShiftSchedule(courier)) {
            return true;
        }
        LocalTime now = currentShiftTime();
        LocalTime start = parseShift(courier.getShiftStart(), "06:00");
        LocalTime end = parseShift(courier.getShiftEnd(), "14:00");
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    public boolean usesShiftSchedule(Courier courier) {
        return config.courierShiftsEnabled() && !config.courierShiftsTestMode() && !courier.isTestModeEnabled();
    }

    private LocalTime currentShiftTime() {
        String override = config.courierShiftsClockOverride();
        if (!override.isBlank()) {
            return parseShift(override, "08:00");
        }
        return LocalTime.now();
    }

    public MapPoint centralPoint() {
        return mapService.central();
    }

    public void updateLiveLocation(long courierId, long mapPointId, Double latitude, Double longitude,
                                   String locationLabel, boolean recordHistory, boolean logImportant) {
        Courier courier = requireCourier(courierId);
        mapRepository.findById(mapPointId)
                .orElseThrow(() -> new ValidationException("Punkt mapy nie istnieje."));
        courierRepository.updateLocation(courierId, mapPointId, latitude, longitude, locationLabel, recordHistory);
        if (logImportant) {
            reportService.info("Kurier " + courier.getFullName() + " zmienił lokalizację.");
        }
    }

    public void clearTransientRouteLabel(long courierId) {
        Courier courier = requireCourier(courierId);
        MapPoint point = mapRepository.findById(courier.getCurrentMapPointId())
                .orElseThrow(() -> new ValidationException("Punkt mapy nie istnieje."));
        String label = courier.getCurrentLatitude() != null && courier.getCurrentLongitude() != null
                && !sameCoordinate(courier.getCurrentLatitude(), courier.getCurrentLongitude(), point)
                ? "Aktualna pozycja kuriera"
                : point.getName();
        courierRepository.updateLocation(courierId, courier.getCurrentMapPointId(),
                courier.getCurrentLatitude(), courier.getCurrentLongitude(), label, false);
    }

    private boolean sameCoordinate(double latitude, double longitude, MapPoint point) {
        return Math.abs(latitude - point.getLatitude()) < 0.000001
                && Math.abs(longitude - point.getLongitude()) < 0.000001;
    }

    public GeoCoordinate coordinateOf(Courier courier) {
        if (courier.getCurrentLatitude() != null && courier.getCurrentLongitude() != null) {
            return new GeoCoordinate(courier.getCurrentLatitude(), courier.getCurrentLongitude());
        }
        MapPoint point = mapService.requirePoint(courier.getCurrentMapPointId());
        return mapService.coordinateOf(point);
    }

    private RouteResult routeFromCourier(Courier courier, long pickupMapPointId) {
        MapPoint target = mapService.requirePoint(pickupMapPointId);
        return mapService.shortestRoute(coordinateOf(courier), mapService.coordinateOf(target),
                courier.getCurrentPointName(), target.getName());
    }

    private String normalizeShift(String value, String fallback) {
        return parseShift(value, fallback).toString();
    }

    private LocalTime parseShift(String value, String fallback) {
        try {
            return LocalTime.parse(value == null || value.isBlank() ? fallback : value.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException("Godzina zmiany kuriera musi mieć format HH:mm.");
        }
    }

    private record CourierDistance(Courier courier, RouteResult route) {
    }
}
