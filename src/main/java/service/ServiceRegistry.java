package service;

import database.DatabaseManager;
import database.SeedService;
import repository.ClientAddressRepository;
import repository.CourierRepository;
import repository.EventLogRepository;
import repository.MapRepository;
import repository.NotificationRepository;
import repository.ParcelRepository;
import repository.UserRepository;
import simulation.SimulationService;
import util.AppConfig;

public class ServiceRegistry {
    private final AppConfig config;
    private final ReportService reportService;
    private final UserService userService;
    private final AddressService addressService;
    private final MapService mapService;
    private final CourierService courierService;
    private final ParcelService parcelService;
    private final FleetSnapshotService fleetSnapshotService;
    private final NotificationService notificationService;
    private final SessionService sessionService;
    private final SimulationService simulationService;

    public ServiceRegistry(AppConfig config) {
        this.config = config;
        DatabaseManager databaseManager = new DatabaseManager(config);
        databaseManager.initializeSchema();
        UserRepository userRepository = new UserRepository(databaseManager);
        ClientAddressRepository addressRepository = new ClientAddressRepository(databaseManager);
        CourierRepository courierRepository = new CourierRepository(databaseManager);
        MapRepository mapRepository = new MapRepository(databaseManager);
        ParcelRepository parcelRepository = new ParcelRepository(databaseManager);
        NotificationRepository notificationRepository = new NotificationRepository(databaseManager);
        EventLogRepository eventLogRepository = new EventLogRepository(databaseManager);
        this.reportService = new ReportService(eventLogRepository, config);
        this.userService = new UserService(userRepository);
        this.mapService = new MapService(mapRepository, config);
        this.addressService = new AddressService(addressRepository, mapService);
        PriceService priceService = new PriceService(mapService);
        this.courierService = new CourierService(courierRepository, mapRepository, mapService, reportService, config);
        this.parcelService = new ParcelService(parcelRepository, courierRepository, courierService,
                addressService, mapService, priceService, reportService);
        this.fleetSnapshotService = new FleetSnapshotService(courierService, parcelService, mapService);
        this.notificationService = new NotificationService(notificationRepository, userRepository, reportService);
        this.sessionService = new SessionService();
        this.simulationService = new SimulationService(courierService, parcelService, mapService,
                reportService, config, config.simulationIntervalSeconds());
        SeedService seedService = new SeedService(userRepository, addressRepository, courierRepository,
                mapRepository, parcelRepository, priceService, reportService);
        seedService.seedIfEmpty(config.seedEnabled());
        this.parcelService.enforceIdleCourierShifts();
        this.simulationService.start();
    }

    public AppConfig config() {
        return config;
    }

    public UserService userService() {
        return userService;
    }

    public AddressService addressService() {
        return addressService;
    }

    public MapService mapService() {
        return mapService;
    }

    public CourierService courierService() {
        return courierService;
    }

    public ParcelService parcelService() {
        return parcelService;
    }

    public FleetSnapshotService fleetSnapshotService() {
        return fleetSnapshotService;
    }

    public NotificationService notificationService() {
        return notificationService;
    }

    public SessionService sessionService() {
        return sessionService;
    }

    public SimulationService simulationService() {
        return simulationService;
    }

    public ReportService reportService() {
        return reportService;
    }

}
