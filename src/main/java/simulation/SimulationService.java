package simulation;

import client.map.GeoCoordinate;
import client.map.OsrmRoute;
import model.Courier;
import model.CourierStatus;
import model.MapPoint;
import model.Parcel;
import model.ParcelStatus;
import service.CourierService;
import service.MapService;
import service.ParcelService;
import service.ReportService;
import util.AppConfig;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimulationService {
    private final CourierService courierService;
    private final ParcelService parcelService;
    private final MapService mapService;
    private final ReportService reportService;
    private final int intervalSeconds;
    private final double movementPerTickMeters;
    private final int pickupCooldownSeconds;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<Long, CourierTaskState> taskStates = new HashMap<>();
    private final Map<Long, ReturnToCentralState> returnStates = new HashMap<>();
    private ScheduledFuture<?> future;
    private int tick;

    public SimulationService(CourierService courierService, ParcelService parcelService,
                             MapService mapService, ReportService reportService, AppConfig config, int intervalSeconds) {
        this.courierService = courierService;
        this.parcelService = parcelService;
        this.mapService = mapService;
        this.reportService = reportService;
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.movementPerTickMeters = Math.max(1.0, config.simulationCourierSpeedKmh() * 1000.0 / 3600.0 * this.intervalSeconds);
        this.pickupCooldownSeconds = Math.max(1, config.simulationPickupCooldownSeconds());
    }

    public synchronized boolean start() {
        if (running.get()) {
            return false;
        }
        running.set(true);
        future = scheduler.scheduleAtFixedRate(this::tickSafely, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        reportService.info("Uruchomiono symulację kurierów.");
        return true;
    }

    public synchronized boolean stop() {
        if (!running.get()) {
            return false;
        }
        running.set(false);
        if (future != null) {
            future.cancel(false);
        }
        taskStates.clear();
        returnStates.clear();
        reportService.info("Zatrzymano symulację kurierów.");
        return true;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    private void tickSafely() {
        try {
            runTick();
        } catch (RuntimeException e) {
            reportService.error("Błąd symulacji: " + e.getMessage());
        }
    }

    private void runTick() {
        if (!running.get()) {
            return;
        }
        tick++;
        List<Courier> couriers = courierService.listCouriers();
        for (Courier courier : couriers) {
            handleCourier(courier);
        }
        taskStates.keySet().removeIf(courierId -> couriers.stream().noneMatch(courier -> courier.getId() == courierId));
        returnStates.keySet().removeIf(courierId -> couriers.stream().noneMatch(courier -> courier.getId() == courierId));
        parcelService.processWaitingQueue();
    }

    private void handleCourier(Courier courier) {
        List<Parcel> activeParcels = parcelService.activeForCourier(courier.getId());
        if (activeParcels.isEmpty()) {
            taskStates.remove(courier.getId());
            handleShiftWithoutParcel(courier);
            return;
        }

        returnStates.remove(courier.getId());
        CourierTaskState existingState = taskStates.get(courier.getId());
        Parcel parcel = selectParcel(courier, activeParcels, existingState);
        if (parcel == null) {
            taskStates.remove(courier.getId());
            return;
        }

        CourierTaskState state = existingState;
        if (state == null || state.parcelId() != parcel.getId() || !stateMatchesStatus(state, parcel.getStatus())) {
            state = createState(courier, parcel);
            taskStates.put(courier.getId(), state);
        }

        CourierTaskState nextState = advanceState(courier, parcel, state);
        if (nextState == null) {
            taskStates.remove(courier.getId());
        } else {
            taskStates.put(courier.getId(), nextState);
        }
    }

    private void handleShiftWithoutParcel(Courier courier) {
        if (!courierService.usesShiftSchedule(courier)) {
            returnStates.remove(courier.getId());
            return;
        }
        if (courierService.isWithinWorkingWindow(courier)) {
            returnStates.remove(courier.getId());
            if (courier.getStatus() == CourierStatus.OFFLINE) {
                MapPoint central = courierService.centralPoint();
                courierService.updateLiveLocation(courier.getId(), central.getId(), central.getLatitude(), central.getLongitude(),
                        central.getName(), true, false);
                courierService.updateStatus(courier.getId(), CourierStatus.AVAILABLE);
                parcelService.processWaitingQueue();
            }
            return;
        }
        if (courier.getStatus() == CourierStatus.OFFLINE) {
            returnStates.remove(courier.getId());
            return;
        }
        advanceReturnToCentral(courier);
    }

    private void advanceReturnToCentral(Courier courier) {
        MapPoint central = courierService.centralPoint();
        GeoCoordinate courierCoordinate = courierService.coordinateOf(courier);
        GeoCoordinate centralCoordinate = mapService.coordinateOf(central);
        if (sameCoordinate(courierCoordinate, central)) {
            courierService.updateLiveLocation(courier.getId(), central.getId(), central.getLatitude(), central.getLongitude(),
                    central.getName(), true, false);
            courierService.updateStatus(courier.getId(), CourierStatus.OFFLINE);
            returnStates.remove(courier.getId());
            return;
        }
        if (courier.getStatus() == CourierStatus.AVAILABLE) {
            courierService.updateStatus(courier.getId(), CourierStatus.BREAK);
        }
        ReturnToCentralState state = returnStates.get(courier.getId());
        if (state == null) {
            OsrmRoute route = mapService.routeBetween(courierCoordinate, centralCoordinate);
            state = new ReturnToCentralState(central.getId(), central.getName(), route, route.distanceMeters(), 0);
        }
        double nextDistance = Math.min(state.totalDistanceMeters(), state.travelledMeters() + movementPerTickMeters);
        GeoCoordinate position = interpolate(state.route(), nextDistance);
        courierService.updateLiveLocation(courier.getId(), courier.getCurrentMapPointId(),
                position.latitude(), position.longitude(), "Wraca do Centrali po zmianie", false, false);
        if (nextDistance + 0.5 < state.totalDistanceMeters()) {
            returnStates.put(courier.getId(), state.withTravelledMeters(nextDistance));
            return;
        }
        courierService.updateLiveLocation(courier.getId(), central.getId(), central.getLatitude(), central.getLongitude(),
                central.getName(), true, false);
        courierService.updateStatus(courier.getId(), CourierStatus.OFFLINE);
        returnStates.remove(courier.getId());
    }

    private Parcel selectParcel(Courier courier, List<Parcel> activeParcels, CourierTaskState existingState) {
        if (existingState != null) {
            for (Parcel parcel : activeParcels) {
                if (parcel.getId() == existingState.parcelId()) {
                    return parcel;
                }
            }
        }
        return activeParcels.stream()
                .min(Comparator.comparingInt((Parcel parcel) -> phasePriority(parcel.getStatus()))
                        .thenComparingInt(parcel -> travelMinutesFromCourier(courier, parcel)))
                .orElse(null);
    }

    private int travelMinutesFromCourier(Courier courier, Parcel parcel) {
        MapPoint target = switch (expectedPhase(parcel.getStatus())) {
            case TO_PICKUP, PICKUP_COOLDOWN -> mapService.requirePoint(parcel.getSenderMapPointId());
            case TO_DELIVERY -> mapService.requirePoint(parcel.getReceiverMapPointId());
        };
        return mapService.shortestRoute(courierService.coordinateOf(courier), mapService.coordinateOf(target),
                courier.getCurrentPointName(), target.getName()).getMinutes();
    }

    private int phasePriority(ParcelStatus status) {
        return switch (expectedPhase(status)) {
            case TO_DELIVERY -> 0;
            case PICKUP_COOLDOWN -> 1;
            case TO_PICKUP -> 2;
        };
    }

    private CourierTaskState createState(Courier courier, Parcel parcel) {
        return switch (expectedPhase(parcel.getStatus())) {
            case TO_PICKUP -> createPickupRoute(courier, parcel);
            case PICKUP_COOLDOWN -> {
                MapPoint pickup = mapService.requirePoint(parcel.getSenderMapPointId());
                courierService.updateLiveLocation(courier.getId(), pickup.getId(), pickup.getLatitude(), pickup.getLongitude(),
                        pickup.getName(), true, false);
                yield new CooldownState(parcel.getId(), pickup.getId(), pickup.getName(),
                        mapService.coordinateOf(pickup), pickupCooldownSeconds);
            }
            case TO_DELIVERY -> createDeliveryRoute(courier, parcel);
        };
    }

    private CourierTaskState advanceState(Courier courier, Parcel parcel, CourierTaskState state) {
        return switch (state) {
            case RouteState routeState -> advanceRouteState(courier, parcel, routeState);
            case CooldownState cooldownState -> advanceCooldownState(parcel, cooldownState);
        };
    }

    private CourierTaskState advanceRouteState(Courier courier, Parcel parcel, RouteState state) {
        double nextDistance = Math.min(state.totalDistanceMeters(), state.travelledMeters() + movementPerTickMeters);
        GeoCoordinate position = interpolate(state.route(), nextDistance);
        String label = switch (state.phase()) {
            case TO_PICKUP -> "W drodze do odbioru: " + state.targetLabel();
            case TO_DELIVERY -> "W drodze do dostawy: " + state.targetLabel();
            case PICKUP_COOLDOWN -> state.targetLabel();
        };
        courierService.updateLiveLocation(courier.getId(), courier.getCurrentMapPointId(),
                position.latitude(), position.longitude(), label, false, false);

        if (nextDistance + 0.5 < state.totalDistanceMeters()) {
            return state.withTravelledMeters(nextDistance);
        }

        MapPoint target = mapService.requirePoint(state.targetMapPointId());
        courierService.updateLiveLocation(courier.getId(), target.getId(), target.getLatitude(), target.getLongitude(),
                target.getName(), true, false);
        if (state.phase() == TaskPhase.TO_PICKUP) {
            parcelService.updateStatusForSimulation(parcel.getId(), ParcelStatus.PICKUP_IN_PROGRESS,
                    "Kurier dotarł do nadawcy.");
            return new CooldownState(parcel.getId(), target.getId(), target.getName(),
                    mapService.coordinateOf(target), pickupCooldownSeconds);
        }
        parcelService.updateStatusForSimulation(parcel.getId(), ParcelStatus.DELIVERED,
                "Paczka została doręczona w symulacji.");
        return null;
    }

    private CourierTaskState advanceCooldownState(Parcel parcel, CooldownState state) {
        int remaining = Math.max(0, state.remainingSeconds() - intervalSeconds);
        if (remaining > 0) {
            return state.withRemainingSeconds(remaining);
        }
        Courier courier = courierService.requireCourier(parcel.getAssignedCourierId());
        parcelService.updateStatusForSimulation(parcel.getId(), ParcelStatus.IN_TRANSIT,
                "Paczka jest w drodze.");
        return createDeliveryRoute(courier, parcel);
    }

    private RouteState createPickupRoute(Courier courier, Parcel parcel) {
        MapPoint pickup = mapService.requirePoint(parcel.getSenderMapPointId());
        OsrmRoute route = mapService.routeBetween(courierService.coordinateOf(courier), mapService.coordinateOf(pickup));
        return new RouteState(parcel.getId(), TaskPhase.TO_PICKUP, pickup.getId(), pickup.getName(),
                route, route.distanceMeters(), 0);
    }

    private RouteState createDeliveryRoute(Courier courier, Parcel parcel) {
        MapPoint receiver = mapService.requirePoint(parcel.getReceiverMapPointId());
        OsrmRoute route = mapService.routeBetween(courierService.coordinateOf(courier), mapService.coordinateOf(receiver));
        return new RouteState(parcel.getId(), TaskPhase.TO_DELIVERY, receiver.getId(), receiver.getName(),
                route, route.distanceMeters(), 0);
    }

    private TaskPhase expectedPhase(ParcelStatus status) {
        return switch (status) {
            case OUT_FOR_DELIVERY, WAREHOUSE, IN_TRANSIT -> TaskPhase.TO_DELIVERY;
            case PICKUP_IN_PROGRESS -> TaskPhase.PICKUP_COOLDOWN;
            case WAITING_FOR_COURIER -> TaskPhase.TO_PICKUP;
            default -> TaskPhase.TO_DELIVERY;
        };
    }

    private boolean stateMatchesStatus(CourierTaskState state, ParcelStatus status) {
        if (status == ParcelStatus.PICKUP_IN_PROGRESS) {
            return state.phase() == TaskPhase.PICKUP_COOLDOWN;
        }
        return state.phase() == expectedPhase(status);
    }

    private GeoCoordinate interpolate(OsrmRoute route, double travelledMeters) {
        List<GeoCoordinate> geometry = route.geometry();
        if (geometry.isEmpty()) {
            return new GeoCoordinate(50.06143, 19.93658);
        }
        if (geometry.size() == 1 || travelledMeters <= 0) {
            return geometry.getFirst();
        }

        double remaining = travelledMeters;
        GeoCoordinate previous = geometry.getFirst();
        for (int i = 1; i < geometry.size(); i++) {
            GeoCoordinate next = geometry.get(i);
            double segmentMeters = distanceMeters(previous, next);
            if (remaining <= segmentMeters || i == geometry.size() - 1) {
                if (segmentMeters <= 0.001) {
                    return next;
                }
                double ratio = Math.max(0, Math.min(1, remaining / segmentMeters));
                double latitude = previous.latitude() + (next.latitude() - previous.latitude()) * ratio;
                double longitude = previous.longitude() + (next.longitude() - previous.longitude()) * ratio;
                return new GeoCoordinate(latitude, longitude);
            }
            remaining -= segmentMeters;
            previous = next;
        }
        return geometry.getLast();
    }

    private double distanceMeters(GeoCoordinate first, GeoCoordinate second) {
        double earthRadiusKm = 6371.0088;
        double deltaLat = Math.toRadians(second.latitude() - first.latitude());
        double deltaLon = Math.toRadians(second.longitude() - first.longitude());
        double startLat = Math.toRadians(first.latitude());
        double endLat = Math.toRadians(second.latitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(startLat) * Math.cos(endLat)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c * 1000.0;
    }

    private boolean sameCoordinate(GeoCoordinate coordinate, MapPoint point) {
        return Math.abs(coordinate.latitude() - point.getLatitude()) < 0.000001
                && Math.abs(coordinate.longitude() - point.getLongitude()) < 0.000001;
    }

    private sealed interface CourierTaskState permits RouteState, CooldownState {
        long parcelId();

        TaskPhase phase();
    }

    private record RouteState(long parcelId, TaskPhase phase, long targetMapPointId, String targetLabel,
                              OsrmRoute route, double totalDistanceMeters, double travelledMeters)
            implements CourierTaskState {
        RouteState withTravelledMeters(double newDistance) {
            return new RouteState(parcelId, phase, targetMapPointId, targetLabel, route, totalDistanceMeters, newDistance);
        }
    }

    private record CooldownState(long parcelId, long targetMapPointId, String targetLabel,
                                 GeoCoordinate coordinate, int remainingSeconds)
            implements CourierTaskState {
        @Override
        public TaskPhase phase() {
            return TaskPhase.PICKUP_COOLDOWN;
        }

        CooldownState withRemainingSeconds(int seconds) {
            return new CooldownState(parcelId, targetMapPointId, targetLabel, coordinate, seconds);
        }
    }

    private record ReturnToCentralState(long targetMapPointId, String targetLabel, OsrmRoute route,
                                        double totalDistanceMeters, double travelledMeters) {
        ReturnToCentralState withTravelledMeters(double newDistance) {
            return new ReturnToCentralState(targetMapPointId, targetLabel, route, totalDistanceMeters, newDistance);
        }
    }

    private enum TaskPhase {
        TO_PICKUP,
        PICKUP_COOLDOWN,
        TO_DELIVERY
    }
}
