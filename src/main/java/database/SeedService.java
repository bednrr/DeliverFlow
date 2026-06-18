package database;

import model.ClientAddress;
import model.Courier;
import model.CourierStatus;
import model.MapPoint;
import model.Parcel;
import model.ParcelSize;
import model.ParcelStatus;
import model.Theme;
import model.User;
import model.UserRole;
import repository.ClientAddressRepository;
import repository.CourierRepository;
import repository.MapRepository;
import repository.ParcelRepository;
import repository.UserRepository;
import service.PriceService;
import service.ReportService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeedService {
    private final UserRepository userRepository;
    private final ClientAddressRepository addressRepository;
    private final CourierRepository courierRepository;
    private final MapRepository mapRepository;
    private final ParcelRepository parcelRepository;
    private final PriceService priceService;
    private final ReportService reportService;

    public SeedService(UserRepository userRepository, ClientAddressRepository addressRepository,
                       CourierRepository courierRepository, MapRepository mapRepository,
                       ParcelRepository parcelRepository, PriceService priceService, ReportService reportService) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.courierRepository = courierRepository;
        this.mapRepository = mapRepository;
        this.parcelRepository = parcelRepository;
        this.priceService = priceService;
        this.reportService = reportService;
    }

    public void seedIfEmpty(boolean enabled) {
        if (!enabled) {
            return;
        }
        if (userRepository.count() == 0 || mapRepository.countPoints() == 0) {
            seed();
            reportService.info("Utworzono dane startowe DeliverFlow.");
        }
    }

    private void seed() {
        Map<String, MapPoint> points = seedMap();

        User admin = user("Anna", "Admin", "admin@deliverflow.local", "500100100", "admin123", UserRole.ADMIN);
        User dispatcher = user("Daria", "Dyspozytor", "dyspozytor@deliverflow.local", "500200200", "dyspozytor123", UserRole.DISPATCHER);
        User client1 = user("Jan", "Kowalski", "klient1@deliverflow.local", "500300301", "klient123", UserRole.CLIENT);
        User client2 = user("Marta", "Nowak", "klient2@deliverflow.local", "500300302", "klient123", UserRole.CLIENT);

        List<User> courierUsers = List.of(
                user("Piotr", "Kurier", "kurier1@deliverflow.local", "500400401", "kurier123", UserRole.COURIER),
                user("Ewa", "Szybka", "kurier2@deliverflow.local", "500400402", "kurier123", UserRole.COURIER),
                user("Tomasz", "Trasa", "kurier3@deliverflow.local", "500400403", "kurier123", UserRole.COURIER),
                user("Karol", "Bronowicki", "kurier4@deliverflow.local", "500400404", "kurier123", UserRole.COURIER),
                user("Natalia", "Hutnicza", "kurier5@deliverflow.local", "500400405", "kurier123", UserRole.COURIER),
                user("Michał", "Kazimierski", "kurier6@deliverflow.local", "500400406", "kurier123", UserRole.COURIER),
                user("Alicja", "Ruczaj", "kurier7@deliverflow.local", "500400407", "kurier123", UserRole.COURIER),
                user("Damian", "Podgórski", "kurier8@deliverflow.local", "500400408", "kurier123", UserRole.COURIER),
                user("Kinga", "Wschód", "kurier9@deliverflow.local", "500400409", "kurier123", UserRole.COURIER),
                user("Marcin", "Północ", "kurier10@deliverflow.local", "500400410", "kurier123", UserRole.COURIER),
                user("Sylwia", "Południe", "kurier11@deliverflow.local", "500400411", "kurier123", UserRole.COURIER),
                user("Rafał", "Prądnik", "kurier12@deliverflow.local", "500400412", "kurier123", UserRole.COURIER),
                user("Joanna", "Mistrz", "kurier13@deliverflow.local", "500400413", "kurier123", UserRole.COURIER),
                user("Łukasz", "Lagiewnicki", "kurier14@deliverflow.local", "500400414", "kurier123", UserRole.COURIER),
                user("Patrycja", "Bieżanów", "kurier15@deliverflow.local", "500400415", "kurier123", UserRole.COURIER)
        );

        address(client1, "Dom", "ul. Długa 12, 31-146 Kraków", points.get("Rynek Główny"), "Domofon 12");
        address(client1, "Praca", "ul. Bobrzyńskiego 14, 30-348 Kraków", points.get("Ruczaj"), "");
        address(client2, "Akademik", "ul. Reymonta 17, 30-059 Kraków", points.get("Krowodrza"), "Recepcja");

        List<Courier> couriers = List.of(
                courier(courierUsers.get(0), points.get("Dworzec Główny"), "KR 1001A", CourierStatus.BUSY, "06:00", "14:00"),
                courier(courierUsers.get(1), points.get("Nowa Huta"), "KR 1002B", CourierStatus.AVAILABLE, "06:00", "14:00"),
                courier(courierUsers.get(2), points.get("Bronowice"), "KR 1003C", CourierStatus.BREAK, "06:00", "14:00"),
                courier(courierUsers.get(3), points.get("Ruczaj"), "KR 1004D", CourierStatus.AVAILABLE, "06:00", "14:00"),
                courier(courierUsers.get(4), points.get("Podgórze"), "KR 1005E", CourierStatus.AVAILABLE, "06:00", "14:00"),
                courier(courierUsers.get(5), points.get("Prądnik Biały"), "KR 1006F", CourierStatus.AVAILABLE, "06:00", "14:00"),
                courier(courierUsers.get(6), points.get("Bieżanów"), "KR 1007G", CourierStatus.AVAILABLE, "06:00", "14:00"),
                courier(courierUsers.get(7), points.get("Mistrzejowice"), "KR 1008H", CourierStatus.AVAILABLE, "06:00", "14:00"),
                courier(courierUsers.get(8), points.get("Kurdwanów"), "KR 1009J", CourierStatus.AVAILABLE, "14:00", "22:00"),
                courier(courierUsers.get(9), points.get("Łagiewniki"), "KR 1010K", CourierStatus.AVAILABLE, "14:00", "22:00"),
                courier(courierUsers.get(10), points.get("Krowodrza"), "KR 1011L", CourierStatus.AVAILABLE, "14:00", "22:00"),
                courier(courierUsers.get(11), points.get("Zabłocie"), "KR 1012M", CourierStatus.AVAILABLE, "14:00", "22:00"),
                courier(courierUsers.get(12), points.get("Kazimierz"), "KR 1013N", CourierStatus.AVAILABLE, "14:00", "22:00"),
                courier(courierUsers.get(13), points.get("Rynek Główny"), "KR 1014P", CourierStatus.AVAILABLE, "14:00", "22:00", true),
                courier(courierUsers.get(14), points.get("Centrala"), "KR 1015R", CourierStatus.AVAILABLE, "14:00", "22:00", true)
        );

        parcel(client1, "Dokumenty do podpisu", "Koperta A4", ParcelSize.SMALL, 0.3,
                "Firma Alfa", "501111222", "ul. Długa 12, 31-146 Kraków", points.get("Rynek Główny"),
                "ul. Przemysłowa 4, 30-701 Kraków", points.get("Zabłocie"), ParcelStatus.WAITING_FOR_COURIER, couriers.get(0));
        parcel(client2, "Części komputerowe", "Podzespoły serwisowe", ParcelSize.MEDIUM, 4.2,
                "Serwis Beta", "502222333", "ul. Reymonta 17, 30-059 Kraków", points.get("Krowodrza"),
                "os. Centrum A 2, 31-923 Kraków", points.get("Nowa Huta"), ParcelStatus.WAITING_FOR_COURIER, null);
        parcel(client1, "Prezent", "Paczka urodzinowa", ParcelSize.LARGE, 7.5,
                "Katarzyna Zielińska", "503333444", "ul. Bobrzyńskiego 14, 30-348 Kraków", points.get("Ruczaj"),
                "ul. Wielicka 88, 30-552 Kraków", points.get("Bieżanów"), ParcelStatus.DELIVERED, couriers.get(1));

        courierRepository.updateStatus(couriers.get(0).getId(), CourierStatus.BUSY);
        courierRepository.updateStatus(couriers.get(1).getId(), CourierStatus.AVAILABLE);
        courierRepository.updateStatus(couriers.get(2).getId(), CourierStatus.BREAK);
        reportService.info("Seed: konta startowe administratora, dyspozytora, klientów i kurierów zostały utworzone.");
    }

    private Map<String, MapPoint> seedMap() {
        Map<String, MapPoint> points = new HashMap<>();
        addPoint(points, "Rynek Główny", 50.06178, 19.93737, false);
        addPoint(points, "Dworzec Główny", 50.06765, 19.94719, false);
        addPoint(points, "Kazimierz", 50.04958, 19.94443, false);
        addPoint(points, "Podgórze", 50.04073, 19.95791, false);
        addPoint(points, "Nowa Huta", 50.07295, 20.03791, false);
        addPoint(points, "Ruczaj", 50.01531, 19.89979, false);
        addPoint(points, "Bronowice", 50.08240, 19.88382, false);
        addPoint(points, "Kurdwanów", 50.02033, 19.95859, false);
        addPoint(points, "Prądnik Biały", 50.09445, 19.92198, false);
        addPoint(points, "Bieżanów", 50.01542, 20.02893, false);
        addPoint(points, "Zabłocie", 50.04967, 19.96018, false);
        addPoint(points, "Łagiewniki", 50.02288, 19.93624, false);
        addPoint(points, "Krowodrza", 50.07531, 19.92357, false);
        addPoint(points, "Mistrzejowice", 50.09766, 20.00074, false);
        addPoint(points, "Centrala", 50.072427, 19.943229, true);
        return points;
    }

    private void addPoint(Map<String, MapPoint> points, String name, double latitude, double longitude, boolean warehouse) {
        points.put(name, mapRepository.savePoint(new MapPoint(0, name, latitude, longitude, warehouse)));
    }

    private User user(String firstName, String lastName, String email, String phone, String password, UserRole role) {
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(password);
        user.setRole(role);
        user.setTheme(Theme.LIGHT);
        return userRepository.save(user);
    }

    private void address(User user, String name, String text, MapPoint point, String notes) {
        addressRepository.save(new ClientAddress(0, user.getId(), name, text, point.getId(), notes, null, null));
    }

    private Courier courier(User user, MapPoint point, String vehicle, CourierStatus status, String shiftStart, String shiftEnd) {
        return courier(user, point, vehicle, status, shiftStart, shiftEnd, false);
    }

    private Courier courier(User user, MapPoint point, String vehicle, CourierStatus status, String shiftStart,
                            String shiftEnd, boolean testModeEnabled) {
        Courier courier = new Courier();
        courier.setUserId(user.getId());
        courier.setStatus(status);
        courier.setCurrentMapPointId(point.getId());
        courier.setCurrentLatitude(point.getLatitude());
        courier.setCurrentLongitude(point.getLongitude());
        courier.setCurrentPointName(point.getName());
        courier.setVehicleNumber(vehicle);
        courier.setShiftStart(shiftStart);
        courier.setShiftEnd(shiftEnd);
        courier.setTestModeEnabled(testModeEnabled);
        Courier saved = courierRepository.save(courier);
        courierRepository.updateLocation(saved.getId(), point.getId(), point.getLatitude(), point.getLongitude(), point.getName(), true);
        return saved;
    }

    private void parcel(User sender, String title, String description, ParcelSize size, double weight,
                        String receiver, String phone, String senderAddress, MapPoint senderPoint,
                        String receiverAddress, MapPoint receiverPoint, ParcelStatus status, Courier courier) {
        Parcel parcel = new Parcel();
        parcel.setTitle(title);
        parcel.setDescription(description);
        parcel.setSize(size);
        parcel.setWeightKg(weight);
        parcel.setSenderUserId(sender.getId());
        parcel.setReceiverName(receiver);
        parcel.setReceiverPhone(phone);
        parcel.setSenderAddressText(senderAddress);
        parcel.setSenderMapPointId(senderPoint.getId());
        parcel.setReceiverAddressText(receiverAddress);
        parcel.setReceiverMapPointId(receiverPoint.getId());
        parcel.setStatus(status);
        if (courier != null) {
            parcel.setAssignedCourierId(courier.getId());
        }
        parcel.setEstimatedPrice(priceService.estimate(size, weight, senderPoint.getId(), receiverPoint.getId()));
        parcelRepository.save(parcel);
    }
}
