package service;

import client.map.GeoCoordinate;
import model.Courier;
import model.CourierStatus;
import model.MapPoint;
import model.Parcel;
import model.ParcelStatus;
import model.User;
import model.UserRole;
import protocol.responses.FleetSnapshotEntry;
import protocol.responses.FleetTaskPhase;
import util.ValidationException;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class FleetSnapshotService {
    private final CourierService courierService;
    private final ParcelService parcelService;
    private final MapService mapService;

    public FleetSnapshotService(CourierService courierService, ParcelService parcelService, MapService mapService) {
        this.courierService = courierService;
        this.parcelService = parcelService;
        this.mapService = mapService;
    }

    public List<FleetSnapshotEntry> snapshotFor(User user) {
        if (user.getRole() != UserRole.DISPATCHER && user.getRole() != UserRole.COURIER) {
            throw new ValidationException("Brak uprawnień do mapy floty.");
        }
        Map<Long, MapPoint> points = mapService.listPoints().stream()
                .collect(Collectors.toMap(MapPoint::getId, point -> point));
        Map<Long, List<Parcel>> activeParcelsByCourier = parcelService.listForUser(user).stream()
                .filter(parcel -> parcel.getAssignedCourierId() != null)
                .filter(parcel -> !isTerminalStatus(parcel.getStatus()))
                .collect(Collectors.groupingBy(Parcel::getAssignedCourierId));
        return courierService.listCouriers().stream()
                .filter(courier -> courier.getStatus() != CourierStatus.OFFLINE)
                .map(courier -> snapshotEntry(courier, activeParcelsByCourier.getOrDefault(courier.getId(), List.of()), points))
                .flatMap(List::stream)
                .sorted(Comparator.comparing(FleetSnapshotEntry::courierName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<FleetSnapshotEntry> snapshotEntry(Courier courier, List<Parcel> activeParcels, Map<Long, MapPoint> points) {
        GeoCoordinate coordinate = resolveCourierCoordinate(courier, points);
        if (coordinate == null) {
            return List.of();
        }
        Parcel activeParcel = selectActiveParcel(courier, coordinate, activeParcels, points);
        FleetTaskPhase phase = phaseOf(activeParcel, courier);
        MapPoint targetPoint = targetPoint(activeParcel, phase, points);
        return List.of(new FleetSnapshotEntry(
                courier.getId(),
                courier.getUserId(),
                courier.getFullName(),
                courier.getStatus(),
                courier.getVehicleNumber(),
                courier.getCurrentMapPointId(),
                coordinate.latitude(),
                coordinate.longitude(),
                courier.getCurrentPointName(),
                courier.getUpdatedAt(),
                activeParcel == null ? null : activeParcel.getId(),
                activeParcel == null ? null : activeParcel.getStatus(),
                activeParcels.size(),
                phase,
                targetPoint == null ? null : targetPoint.getId()
        ));
    }

    private GeoCoordinate resolveCourierCoordinate(Courier courier, Map<Long, MapPoint> points) {
        if (courier.getCurrentLatitude() != null && courier.getCurrentLongitude() != null) {
            return new GeoCoordinate(courier.getCurrentLatitude(), courier.getCurrentLongitude());
        }
        MapPoint point = points.get(courier.getCurrentMapPointId());
        return point == null ? null : new GeoCoordinate(point.getLatitude(), point.getLongitude());
    }

    private Parcel selectActiveParcel(Courier courier, GeoCoordinate coordinate, List<Parcel> parcels, Map<Long, MapPoint> points) {
        return parcels.stream()
                .min(Comparator.comparingInt((Parcel parcel) -> phaseOf(parcel, courier).priority())
                        .thenComparingDouble(parcel -> targetDistance(coordinate,
                                targetPoint(parcel, phaseOf(parcel, courier), points))))
                .orElse(null);
    }

    private double targetDistance(GeoCoordinate courierCoordinate, MapPoint targetPoint) {
        if (courierCoordinate == null || targetPoint == null) {
            return Double.MAX_VALUE;
        }
        double latDiff = courierCoordinate.latitude() - targetPoint.getLatitude();
        double lonDiff = courierCoordinate.longitude() - targetPoint.getLongitude();
        return latDiff * latDiff + lonDiff * lonDiff;
    }

    private MapPoint targetPoint(Parcel parcel, FleetTaskPhase phase, Map<Long, MapPoint> points) {
        if (phase == FleetTaskPhase.RETURN_TO_CENTRAL) {
            return warehousePoint(points);
        }
        if (parcel == null) {
            return null;
        }
        return switch (phase) {
            case TO_PICKUP, PICKUP_COOLDOWN -> points.get(parcel.getSenderMapPointId());
            case TO_DELIVERY -> points.get(parcel.getReceiverMapPointId());
            case RETURN_TO_CENTRAL -> warehousePoint(points);
            case IDLE -> null;
        };
    }

    private MapPoint warehousePoint(Map<Long, MapPoint> points) {
        return points.values().stream()
                .filter(MapPoint::isWarehouse)
                .findFirst()
                .orElse(null);
    }

    private FleetTaskPhase phaseOf(Parcel parcel, Courier courier) {
        if (parcel == null) {
            return isReturningToCentral(courier) ? FleetTaskPhase.RETURN_TO_CENTRAL : FleetTaskPhase.IDLE;
        }
        return switch (parcel.getStatus()) {
            case OUT_FOR_DELIVERY, WAREHOUSE, IN_TRANSIT -> FleetTaskPhase.TO_DELIVERY;
            case PICKUP_IN_PROGRESS -> FleetTaskPhase.PICKUP_COOLDOWN;
            case WAITING_FOR_COURIER -> FleetTaskPhase.TO_PICKUP;
            default -> FleetTaskPhase.IDLE;
        };
    }

    private boolean isReturningToCentral(Courier courier) {
        String currentPointName = courier.getCurrentPointName();
        return currentPointName != null
                && normalizeSearch(currentPointName).contains("wraca do centrali");
    }

    private String normalizeSearch(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private boolean isTerminalStatus(ParcelStatus status) {
        return status == ParcelStatus.CANCELED
                || status == ParcelStatus.DELIVERED
                || status == ParcelStatus.DELIVERY_PROBLEM;
    }
}
