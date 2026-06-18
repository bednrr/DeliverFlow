package service;

import model.Courier;
import model.CourierStatus;
import model.MapPoint;
import model.Parcel;
import model.ParcelSize;
import model.ParcelStatus;
import model.User;
import model.UserRole;
import repository.CourierRepository;
import repository.ParcelRepository;
import util.PhoneUtil;
import util.ValidationException;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ParcelService {
    private final ParcelRepository parcelRepository;
    private final CourierRepository courierRepository;
    private final CourierService courierService;
    private final AddressService addressService;
    private final MapService mapService;
    private final PriceService priceService;
    private final ReportService reportService;
    private final Lock assignmentLock = new ReentrantLock();

    public ParcelService(ParcelRepository parcelRepository, CourierRepository courierRepository,
                         CourierService courierService, AddressService addressService,
                         MapService mapService, PriceService priceService, ReportService reportService) {
        this.parcelRepository = parcelRepository;
        this.courierRepository = courierRepository;
        this.courierService = courierService;
        this.addressService = addressService;
        this.mapService = mapService;
        this.priceService = priceService;
        this.reportService = reportService;
    }

    public AssignmentResult createParcel(User sender, String title, String description, ParcelSize size, double weightKg,
                                         String receiverName, String receiverPhone, String senderAddressText,
                                         long senderMapPointId, String receiverAddressText, long receiverMapPointId,
                                         boolean saveSenderAddress, String senderAddressName,
                                         boolean saveReceiverAddress, String receiverAddressName) {
        requireRole(sender, UserRole.CLIENT);
        validateParcel(title, size, weightKg, receiverName, receiverPhone, senderAddressText, receiverAddressText);
        String normalizedReceiverPhone = PhoneUtil.requireValid(receiverPhone);
        if (sameAddress(senderAddressText, receiverAddressText)) {
            throw new ValidationException("Adres odbioru nie może być taki sam jak adres nadania.");
        }
        MapPoint senderPoint = mapService.resolveOrCreatePoint(senderAddressText, senderMapPointId);
        MapPoint receiverPoint = mapService.resolveOrCreatePoint(receiverAddressText, receiverMapPointId);
        if (saveSenderAddress) {
            addressService.saveAddress(sender.getId(), null, defaultName(senderAddressName, "Nadanie"),
                    senderAddressText, senderPoint.getId(), "");
        }
        if (saveReceiverAddress) {
            addressService.saveAddress(sender.getId(), null, defaultName(receiverAddressName, "Odbiorca"),
                    receiverAddressText, receiverPoint.getId(), "");
        }
        Parcel parcel = new Parcel();
        parcel.setTitle(title);
        parcel.setDescription(description);
        parcel.setSize(size);
        parcel.setWeightKg(weightKg);
        parcel.setSenderUserId(sender.getId());
        parcel.setReceiverName(receiverName);
        parcel.setReceiverPhone(normalizedReceiverPhone);
        parcel.setSenderAddressText(senderAddressText);
        parcel.setSenderMapPointId(senderPoint.getId());
        parcel.setReceiverAddressText(receiverAddressText);
        parcel.setReceiverMapPointId(receiverPoint.getId());
        parcel.setStatus(ParcelStatus.WAITING_FOR_COURIER);
        parcel.setEstimatedPrice(priceService.estimate(size, weightKg, senderPoint.getId(), receiverPoint.getId()));
        Parcel saved = parcelRepository.save(parcel);
        reportService.info("Utworzono paczkę #" + saved.getId() + ": " + saved.getTitle() + ".");
        return assignClosestCourierOrQueue(saved);
    }

    public AssignmentResult assignClosestCourierOrQueue(Parcel parcel) {
        assignmentLock.lock();
        try {
            return courierService.findClosestAssignable(assignmentTargetMapPointId(parcel))
                    .map(courier -> assignCourierInternal(parcel, courier,
                            "Automatycznie przypisano kuriera."))
                    .orElseGet(() -> {
                        ParcelStatus queueStatus = parcel.getStatus() == ParcelStatus.WAREHOUSE
                                ? ParcelStatus.WAREHOUSE
                                : ParcelStatus.WAITING_FOR_COURIER;
                        parcelRepository.updateStatus(parcel.getId(), queueStatus,
                                queueStatus == ParcelStatus.WAREHOUSE
                                        ? "Paczka czeka w centrali na przypisanie do kuriera."
                                        : "Paczka czeka na przypisanie do kuriera.");
                        Parcel updated = parcelRepository.findById(parcel.getId()).orElseThrow();
                        reportService.info("Brak dostępnego kuriera dla paczki #" + parcel.getId()
                                + ". Paczka czeka na przypisanie.");
                        return new AssignmentResult(false,
                                "Paczka czeka na przypisanie kuriera.",
                                updated, null);
                    });
        } finally {
            assignmentLock.unlock();
        }
    }

    public AssignmentResult manualAssign(long parcelId, long courierId, User actor) {
        requireRole(actor, UserRole.DISPATCHER, UserRole.ADMIN);
        assignmentLock.lock();
        try {
            Parcel parcel = parcelRepository.findById(parcelId)
                    .orElseThrow(() -> new ValidationException("Nie znaleziono paczki."));
            if (!canAssignCourier(parcel)) {
                throw new ValidationException(unassignableParcelMessage());
            }
            Courier courier = courierService.requireCourier(courierId);
            validateCourierCanTakeParcel(courier);
            AssignmentResult result = assignCourierInternal(parcel, courier,
                    "Ręcznie przypisano kuriera przez użytkownika " + actor.getFullName() + ".");
            reportService.info("Dyspozycja ręczna: paczka #" + parcelId + " -> kurier " + courier.getFullName() + ".");
            return result;
        } finally {
            assignmentLock.unlock();
        }
    }

    public void processWaitingQueue() {
        assignmentLock.lock();
        try {
            for (Parcel waiting : parcelRepository.findWaiting()) {
                if (courierService.findClosestAssignable(assignmentTargetMapPointId(waiting)).isPresent()) {
                    assignClosestCourierOrQueue(waiting);
                }
            }
        } finally {
            assignmentLock.unlock();
        }
    }

    public List<Parcel> listForUser(User user) {
        return switch (user.getRole()) {
            case CLIENT -> parcelRepository.findBySender(user.getId());
            case COURIER -> courierService.findByUserId(user.getId())
                    .map(courier -> parcelRepository.findByCourier(courier.getId()))
                    .orElse(List.of());
            case DISPATCHER, ADMIN -> parcelRepository.findAll();
        };
    }

    public List<Parcel> listAll() {
        return parcelRepository.findAll();
    }

    public List<?> history(long parcelId, User user) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ValidationException("Nie znaleziono paczki."));
        if (user.getRole() == UserRole.CLIENT && parcel.getSenderUserId() != user.getId()) {
            throw new ValidationException("Brak dostępu do tej paczki.");
        }
        return parcelRepository.history(parcelId);
    }

    public Parcel updateStatus(long parcelId, ParcelStatus status, String note, User actor) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ValidationException("Nie znaleziono paczki."));
        if (actor.getRole() == UserRole.CLIENT) {
            throw new ValidationException("Klient nie może ręcznie zmieniać statusu paczki.");
        }
        if (actor.getRole() == UserRole.COURIER) {
            Courier courier = courierService.findByUserId(actor.getId())
                    .orElseThrow(() -> new ValidationException("Nie znaleziono profilu kuriera."));
            if (parcel.getAssignedCourierId() == null || parcel.getAssignedCourierId() != courier.getId()) {
                throw new ValidationException("Ta paczka nie jest przypisana do zalogowanego kuriera.");
            }
            if (isFinished(parcel.getStatus())) {
                throw new ValidationException("Ta paczka jest zakończona. Nie można zmienić jej statusu.");
            }
            if (status == ParcelStatus.CANCELED) {
                throw new ValidationException("Kurier nie może anulować paczki.");
            }
            if (status == ParcelStatus.WAITING_FOR_COURIER) {
                throw new ValidationException("Kurier nie może cofnąć paczki do statusu oczekiwania na kuriera.");
            }
        }
        if ((status == ParcelStatus.WAITING_FOR_COURIER || status == ParcelStatus.WAREHOUSE)
                && parcel.getAssignedCourierId() != null) {
            parcelRepository.clearAssignmentAndStatus(parcelId, status,
                    defaultName(note, status == ParcelStatus.WAREHOUSE
                            ? "Paczka została odłożona w centrali przez " + actor.getFullName()
                            : "Przywrócono oczekiwanie na kuriera przez " + actor.getFullName()));
            updateCourierStatusAfterAssignmentChange(parcel.getAssignedCourierId());
        } else {
            parcelRepository.updateStatus(parcelId, status, defaultName(note, "Zmiana statusu przez " + actor.getFullName()));
            releaseCourierIfFinished(parcel);
        }
        reportService.info("Zmieniono status paczki #" + parcelId + " na: " + status.displayName() + ".");
        if (isFinished(status) || status == ParcelStatus.WAREHOUSE) {
            processWaitingQueue();
        }
        return parcelRepository.findById(parcelId).orElseThrow();
    }

    public Parcel updateStatusForSimulation(long parcelId, ParcelStatus status, String note) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ValidationException("Nie znaleziono paczki."));
        if (status == ParcelStatus.WAREHOUSE && parcel.getAssignedCourierId() != null) {
            parcelRepository.clearAssignmentAndStatus(parcelId, status, note);
            updateCourierStatusAfterAssignmentChange(parcel.getAssignedCourierId());
        } else {
            parcelRepository.updateStatus(parcelId, status, note);
            releaseCourierIfFinished(parcel);
        }
        if (isFinished(status) || status == ParcelStatus.WAREHOUSE) {
            processWaitingQueue();
        }
        return parcelRepository.findById(parcelId).orElseThrow();
    }

    public Parcel cancelParcel(long parcelId, User user) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ValidationException("Nie znaleziono paczki."));
        if (user.getRole() == UserRole.CLIENT && parcel.getSenderUserId() != user.getId()) {
            throw new ValidationException("Brak dostępu do tej paczki.");
        }
        if (!parcel.getStatus().canBeCanceled()) {
            throw new ValidationException("Tej paczki nie można już anulować.");
        }
        parcelRepository.updateStatus(parcelId, ParcelStatus.CANCELED, "Paczka anulowana przez użytkownika.");
        releaseCourierIfFinished(parcel);
        reportService.info("Anulowano paczkę #" + parcelId + ".");
        processWaitingQueue();
        return parcelRepository.findById(parcelId).orElseThrow();
    }

    public List<Parcel> activeForCourier(long courierId) {
        return parcelRepository.findActiveAssignedToCourier(courierId);
    }

    public void enforceIdleCourierShifts() {
        for (Courier courier : courierService.listCouriers()) {
            if (!activeForCourier(courier.getId()).isEmpty() || !courierService.usesShiftSchedule(courier)) {
                continue;
            }
            if (courierService.isWithinWorkingWindow(courier)) {
                if (courier.getStatus() == CourierStatus.OFFLINE) {
                    MapPoint central = courierService.centralPoint();
                    courierService.updateLiveLocation(courier.getId(), central.getId(), central.getLatitude(),
                            central.getLongitude(), central.getName(), true, false);
                    courierRepository.updateStatus(courier.getId(), CourierStatus.AVAILABLE);
                }
            } else {
                MapPoint central = courierService.centralPoint();
                courierService.updateLiveLocation(courier.getId(), central.getId(), central.getLatitude(),
                        central.getLongitude(), central.getName(), true, false);
                courierRepository.updateStatus(courier.getId(), CourierStatus.OFFLINE);
            }
        }
    }

    private AssignmentResult assignCourierInternal(Parcel parcel, Courier courier, String note) {
        validateCourierCanTakeParcel(courier);
        Long previousCourierId = parcel.getAssignedCourierId();
        ParcelStatus assignedStatus = parcel.getStatus() == ParcelStatus.WAREHOUSE
                ? ParcelStatus.OUT_FOR_DELIVERY
                : ParcelStatus.WAITING_FOR_COURIER;
        courierRepository.updateStatus(courier.getId(), CourierStatus.BUSY);
        parcelRepository.updateAssignmentAndStatus(parcel.getId(), courier.getId(), assignedStatus, note);
        if (previousCourierId != null && previousCourierId != courier.getId()) {
            courierService.clearTransientRouteLabel(previousCourierId);
            updateCourierStatusAfterAssignmentChange(previousCourierId);
        }
        Parcel updated = parcelRepository.findById(parcel.getId()).orElseThrow();
        reportService.info("Przypisano kuriera " + courier.getFullName() + " do paczki #" + parcel.getId() + ".");
        return new AssignmentResult(true,
                "Paczka została przyjęta. Przypisany kurier: " + courier.getFullName() + ".",
                updated, courier);
    }

    private void releaseCourierIfFinished(Parcel parcel) {
        Long courierId = parcel.getAssignedCourierId();
        Parcel latest = parcelRepository.findById(parcel.getId()).orElse(parcel);
        if (courierId == null) {
            courierId = latest.getAssignedCourierId();
        }
        if (courierId != null && isFinished(latest.getStatus())) {
            updateCourierStatusAfterAssignmentChange(courierId);
        }
    }

    private void updateCourierStatusAfterAssignmentChange(long courierId) {
        if (parcelRepository.findActiveAssignedToCourier(courierId).isEmpty()) {
            courierRepository.updateStatus(courierId, CourierStatus.AVAILABLE);
            courierService.clearTransientRouteLabel(courierId);
        } else {
            courierRepository.updateStatus(courierId, CourierStatus.BUSY);
        }
    }

    private boolean isFinished(ParcelStatus status) {
        return status == ParcelStatus.DELIVERED || status == ParcelStatus.CANCELED || status == ParcelStatus.DELIVERY_PROBLEM;
    }

    private boolean canAssignCourier(Parcel parcel) {
        if (isFinished(parcel.getStatus())) {
            return false;
        }
        if (parcel.getStatus() == ParcelStatus.WAREHOUSE) {
            return true;
        }
        return parcel.getStatus() == ParcelStatus.WAITING_FOR_COURIER;
    }

    private String unassignableParcelMessage() {
        return "Nie można przypisać kuriera. Paczka musi oczekiwać na kuriera albo być w centrali.";
    }

    private void validateCourierCanTakeParcel(Courier courier) {
        if (courier.getStatus() != CourierStatus.AVAILABLE) {
            throw new ValidationException("Nie można przypisać kuriera. Kurier musi być dostępny i nie może mieć aktywnego zlecenia.");
        }
        if (!courierService.canAcceptAssignments(courier)) {
            throw new ValidationException("Nie można przypisać kuriera poza jego zmianą.");
        }
        if (!parcelRepository.findActiveAssignedToCourier(courier.getId()).isEmpty()) {
            throw new ValidationException("Nie można przypisać kuriera. Kurier ma już aktywne zlecenie.");
        }
    }

    private long assignmentTargetMapPointId(Parcel parcel) {
        if (parcel.getStatus() == ParcelStatus.WAREHOUSE) {
            return mapService.warehouse().getId();
        }
        return parcel.getSenderMapPointId();
    }

    private boolean sameAddress(String first, String second) {
        return normalizeAddressForComparison(first).equals(normalizeAddressForComparison(second));
    }

    private String normalizeAddressForComparison(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private void validateParcel(String title, ParcelSize size, double weightKg, String receiverName, String receiverPhone,
                                String senderAddressText, String receiverAddressText) {
        requireNotBlank(title, "Tytuł paczki nie może być pusty.");
        if (size == null) {
            throw new ValidationException("Rozmiar paczki jest wymagany.");
        }
        if (weightKg <= 0) {
            throw new ValidationException("Waga paczki musi być większa od zera.");
        }
        requireNotBlank(receiverName, "Dane odbiorcy są wymagane.");
        PhoneUtil.requireValid(receiverPhone);
        requireNotBlank(senderAddressText, "Adres nadania jest wymagany.");
        requireNotBlank(receiverAddressText, "Adres odbioru jest wymagany.");
    }

    private void requireRole(User user, UserRole role) {
        if (user.getRole() != role) {
            throw new ValidationException("Brak uprawnień do tej operacji.");
        }
    }

    private void requireRole(User user, UserRole first, UserRole second) {
        if (user.getRole() != first && user.getRole() != second) {
            throw new ValidationException("Brak uprawnień do tej operacji.");
        }
    }

    private void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
    }

    private String defaultName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
