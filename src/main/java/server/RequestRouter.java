package server;

import model.Courier;
import model.CourierStatus;
import model.Theme;
import model.User;
import model.UserRole;
import protocol.MessageType;
import protocol.ProtocolJson;
import protocol.ProtocolMessage;
import protocol.requests.AccountUpdateRequest;
import protocol.requests.AddressRequest;
import protocol.requests.AssignCourierRequest;
import protocol.requests.CourierStatusRequest;
import protocol.requests.CreateParcelRequest;
import protocol.requests.IdRequest;
import protocol.requests.LoginRequest;
import protocol.requests.NotificationReadRequest;
import protocol.requests.ParcelStatusRequest;
import protocol.requests.RegisterClientRequest;
import protocol.requests.ReportRequest;
import protocol.requests.SaveUserRequest;
import protocol.requests.SendNotificationRequest;
import protocol.requests.TokenRequest;
import protocol.responses.BasicResponse;
import protocol.responses.DataResponse;
import protocol.responses.LoginResponse;
import service.AssignmentResult;
import service.ServiceRegistry;
import util.ValidationException;

public class RequestRouter {
    private final ServiceRegistry services;

    public RequestRouter(ServiceRegistry services) {
        this.services = services;
    }

    public ProtocolMessage handle(ProtocolMessage message) {
        try {
            return switch (message.getType()) {
                case LOGIN_REQUEST -> login(message);
                case REGISTER_CLIENT_REQUEST -> registerClient(message);
                case ACCOUNT_UPDATE_REQUEST -> updateAccount(message);
                case LIST_MAP_POINTS_REQUEST -> ok(MessageType.LIST_MAP_POINTS_RESPONSE,
                        new DataResponse<>(true, "Punkty mapy pobrane.", services.mapService().listPoints()));
                case LIST_ADDRESSES_REQUEST -> listAddresses(message);
                case SAVE_ADDRESS_REQUEST -> saveAddress(message);
                case DELETE_ADDRESS_REQUEST -> deleteAddress(message);
                case CREATE_PARCEL_REQUEST -> createParcel(message);
                case LIST_PARCELS_REQUEST -> listParcels(message);
                case PARCEL_HISTORY_REQUEST -> parcelHistory(message);
                case CANCEL_PARCEL_REQUEST -> cancelParcel(message);
                case UPDATE_PARCEL_STATUS_REQUEST -> updateParcelStatus(message);
                case LIST_COURIERS_REQUEST -> listCouriers(message);
                case LIST_FLEET_SNAPSHOT_REQUEST -> listFleetSnapshot(message);
                case UPDATE_COURIER_STATUS_REQUEST -> updateCourierStatus(message);
                case DISPATCH_PARCEL_REQUEST -> assignCourier(message);
                case LIST_USERS_REQUEST -> listUsers(message);
                case SAVE_USER_REQUEST -> saveUser(message);
                case LIST_EVENTS_REQUEST -> listEvents(message);
                case START_SIMULATION_REQUEST -> startSimulation(message);
                case STOP_SIMULATION_REQUEST -> stopSimulation(message);
                case SIMULATION_STATUS_REQUEST -> simulationStatus(message);
                case LIST_NOTIFICATIONS_REQUEST -> listNotifications(message);
                case LIST_NOTIFICATION_RECIPIENTS_REQUEST -> listNotificationRecipients(message);
                case SEND_NOTIFICATION_REQUEST -> sendNotification(message);
                case MARK_NOTIFICATION_READ_REQUEST -> markNotificationRead(message);
                case MARK_ALL_NOTIFICATIONS_READ_REQUEST -> markAllNotificationsRead(message);
                case LIST_REPORTS_REQUEST -> listReports(message);
                case GENERATE_REPORT_REQUEST -> generateReport(message);
                case READ_REPORT_REQUEST -> readReport(message);
                default -> ProtocolJson.error("Nieobsługiwany typ wiadomości: " + message.getType());
            };
        } catch (ValidationException e) {
            return ProtocolJson.error(e.getMessage());
        } catch (RuntimeException e) {
            services.reportService().error("Błąd obsługi wiadomości " + message.getType() + ": " + e.getMessage());
            return ProtocolJson.error("Wystąpił błąd serwera: " + e.getMessage());
        }
    }

    private ProtocolMessage login(ProtocolMessage message) {
        LoginRequest request = ProtocolJson.fromJson(message.getPayload(), LoginRequest.class);
        User user = services.userService().login(request.email(), request.password());
        String token = services.sessionService().createSession(user);
        services.reportService().info("Logowanie użytkownika: " + user.getEmail() + ".");
        return ok(MessageType.LOGIN_RESPONSE, new LoginResponse(true, "Zalogowano poprawnie.", token, user));
    }

    private ProtocolMessage registerClient(ProtocolMessage message) {
        RegisterClientRequest request = ProtocolJson.fromJson(message.getPayload(), RegisterClientRequest.class);
        User user = services.userService().registerClient(request.firstName(), request.lastName(), request.email(),
                request.phone(), request.password(), request.repeatPassword());
        services.reportService().info("Zarejestrowano klienta: " + user.getEmail() + ".");
        return ok(MessageType.REGISTER_CLIENT_RESPONSE, new DataResponse<>(true, "Konto klienta zostało utworzone.", user));
    }

    private ProtocolMessage updateAccount(ProtocolMessage message) {
        AccountUpdateRequest request = ProtocolJson.fromJson(message.getPayload(), AccountUpdateRequest.class);
        User user = requireUser(request.token());
        User updated = services.userService().updateAccount(user, request.firstName(), request.lastName(),
                request.email(), request.phone(), request.theme(),
                request.oldPassword(), request.newPassword(), request.repeatNewPassword());
        services.sessionService().refresh(request.token(), updated);
        services.reportService().info("Użytkownik " + updated.getEmail() + " zmienił ustawienia konta.");
        return ok(MessageType.ACCOUNT_UPDATE_RESPONSE, new DataResponse<>(true, "Ustawienia konta zostały zapisane.", updated));
    }

    private ProtocolMessage listAddresses(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        return ok(MessageType.LIST_ADDRESSES_RESPONSE,
                new DataResponse<>(true, "Adresy pobrane.", services.addressService().listForUser(user.getId())));
    }

    private ProtocolMessage saveAddress(ProtocolMessage message) {
        AddressRequest request = ProtocolJson.fromJson(message.getPayload(), AddressRequest.class);
        User user = requireUser(request.token());
        services.userService().requireRole(user, UserRole.CLIENT);
        var address = services.addressService().saveAddress(user.getId(), request.id(), request.name(),
                request.addressText(), request.mapPointId(), request.notes());
        return ok(MessageType.SAVE_ADDRESS_RESPONSE, new DataResponse<>(true, "Adres został zapisany.", address));
    }

    private ProtocolMessage deleteAddress(ProtocolMessage message) {
        IdRequest request = ProtocolJson.fromJson(message.getPayload(), IdRequest.class);
        User user = requireUser(request.token());
        services.userService().requireRole(user, UserRole.CLIENT);
        services.addressService().deleteAddress(user.getId(), request.id());
        return ok(MessageType.DELETE_ADDRESS_RESPONSE, new BasicResponse(true, "Adres został usunięty."));
    }

    private ProtocolMessage createParcel(ProtocolMessage message) {
        CreateParcelRequest request = ProtocolJson.fromJson(message.getPayload(), CreateParcelRequest.class);
        User user = requireUser(request.token());
        AssignmentResult result = services.parcelService().createParcel(user, request.title(), request.description(),
                request.size(), request.weightKg(), request.receiverName(), request.receiverPhone(),
                request.senderAddressText(), request.senderMapPointId(), request.receiverAddressText(),
                request.receiverMapPointId(), request.saveSenderAddress(), request.senderAddressName(),
                request.saveReceiverAddress(), request.receiverAddressName());
        return ok(MessageType.CREATE_PARCEL_RESPONSE, new DataResponse<>(true, result.message(), result));
    }

    private ProtocolMessage listParcels(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        if (user.getRole() == UserRole.ADMIN) {
            throw new ValidationException("Administrator nie ma dostępu do paczek.");
        }
        return ok(MessageType.LIST_PARCELS_RESPONSE,
                new DataResponse<>(true, "Paczki pobrane.", services.parcelService().listForUser(user)));
    }

    private ProtocolMessage parcelHistory(ProtocolMessage message) {
        IdRequest request = ProtocolJson.fromJson(message.getPayload(), IdRequest.class);
        User user = requireUser(request.token());
        return ok(MessageType.PARCEL_HISTORY_RESPONSE,
                new DataResponse<>(true, "Historia paczki pobrana.", services.parcelService().history(request.id(), user)));
    }

    private ProtocolMessage cancelParcel(ProtocolMessage message) {
        IdRequest request = ProtocolJson.fromJson(message.getPayload(), IdRequest.class);
        User user = requireUser(request.token());
        return ok(MessageType.CANCEL_PARCEL_RESPONSE,
                new DataResponse<>(true, "Paczka została anulowana.", services.parcelService().cancelParcel(request.id(), user)));
    }

    private ProtocolMessage updateParcelStatus(ProtocolMessage message) {
        ParcelStatusRequest request = ProtocolJson.fromJson(message.getPayload(), ParcelStatusRequest.class);
        User user = requireUser(request.token());
        return ok(MessageType.UPDATE_PARCEL_STATUS_RESPONSE,
                new DataResponse<>(true, "Status paczki został zmieniony.",
                        services.parcelService().updateStatus(request.parcelId(), request.status(), request.note(), user)));
    }

    private ProtocolMessage listCouriers(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        if (user.getRole() == UserRole.CLIENT) {
            throw new ValidationException("Brak uprawnień do listy kurierów.");
        }
        return ok(MessageType.LIST_COURIERS_RESPONSE,
                new DataResponse<>(true, "Kurierzy pobrani.", services.courierService().listCouriers()));
    }

    private ProtocolMessage listFleetSnapshot(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        services.userService().requireRole(user, UserRole.DISPATCHER, UserRole.COURIER);
        return ok(MessageType.LIST_FLEET_SNAPSHOT_RESPONSE,
                new DataResponse<>(true, "Snapshot floty pobrany.", services.fleetSnapshotService().snapshotFor(user)));
    }

    private ProtocolMessage updateCourierStatus(ProtocolMessage message) {
        CourierStatusRequest request = ProtocolJson.fromJson(message.getPayload(), CourierStatusRequest.class);
        User user = requireUser(request.token());
        long courierId = request.courierId();
        if (user.getRole() == UserRole.COURIER) {
            Courier courier = services.courierService().findByUserId(user.getId())
                    .orElseThrow(() -> new ValidationException("Nie znaleziono profilu kuriera."));
            courierId = courier.getId();
        } else {
            services.userService().requireRole(user, UserRole.DISPATCHER);
        }
        if (request.status() != CourierStatus.BUSY
                && !services.parcelService().activeForCourier(courierId).isEmpty()) {
            throw new ValidationException("Kurier ma aktywne paczki, więc jego status musi pozostać Zajęty.");
        }
        services.courierService().updateStatus(courierId, request.status());
        if (request.status() == CourierStatus.AVAILABLE) {
            services.parcelService().processWaitingQueue();
        }
        return ok(MessageType.UPDATE_COURIER_STATUS_RESPONSE, new BasicResponse(true, "Status kuriera został zmieniony."));
    }

    private ProtocolMessage assignCourier(ProtocolMessage message) {
        AssignCourierRequest request = ProtocolJson.fromJson(message.getPayload(), AssignCourierRequest.class);
        User user = requireUser(request.token());
        AssignmentResult result = services.parcelService().manualAssign(request.parcelId(), request.courierId(), user);
        return ok(MessageType.DISPATCH_PARCEL_RESPONSE, new DataResponse<>(true, result.message(), result));
    }

    private ProtocolMessage listUsers(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        services.userService().requireRole(user, UserRole.ADMIN);
        return ok(MessageType.LIST_USERS_RESPONSE,
                new DataResponse<>(true, "Użytkownicy pobrani.", services.userService().listUsers()));
    }

    private ProtocolMessage saveUser(ProtocolMessage message) {
        SaveUserRequest request = ProtocolJson.fromJson(message.getPayload(), SaveUserRequest.class);
        User actor = requireUser(request.token());
        services.userService().requireRole(actor, UserRole.ADMIN);
        Theme theme = request.theme() == null ? Theme.LIGHT : request.theme();
        User saved = services.userService().saveUser(request.id(), request.firstName(), request.lastName(),
                request.email(), request.phone(), request.password(), request.role(), theme, request.blocked());
        if (request.role() == UserRole.COURIER) {
            if (services.courierService().findByUserId(saved.getId()).isEmpty()) {
                long pointId = request.currentMapPointId() == null ? services.mapService().central().getId() : request.currentMapPointId();
                services.courierService().createCourier(saved.getId(), pointId, request.vehicleNumber(),
                        request.shiftStart(), request.shiftEnd(), request.courierTestModeEnabled());
            } else {
                services.courierService().updateVehicleForUser(saved.getId(), request.vehicleNumber());
                services.courierService().updateScheduleForUser(saved.getId(), request.shiftStart(),
                        request.shiftEnd(), request.courierTestModeEnabled());
            }
        }
        if (request.role() != UserRole.COURIER && services.courierService().findByUserId(saved.getId()).isPresent()) {
            services.courierService().removeCourierProfileForUser(saved.getId());
            services.parcelService().processWaitingQueue();
        }
        services.reportService().info("Administrator zapisał konto: " + saved.getEmail() + ".");
        return ok(MessageType.SAVE_USER_RESPONSE, new DataResponse<>(true, "Użytkownik został zapisany.", saved));
    }

    private ProtocolMessage listEvents(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        services.userService().requireRole(user, UserRole.DISPATCHER, UserRole.ADMIN);
        var events = user.getRole() == UserRole.ADMIN
                ? services.reportService().latestEvents(150)
                : services.reportService().latestOperationalEvents(150);
        return ok(MessageType.LIST_EVENTS_RESPONSE,
                new DataResponse<>(true, "Zdarzenia pobrane.", events));
    }

    private ProtocolMessage startSimulation(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        services.userService().requireRole(user, UserRole.DISPATCHER, UserRole.ADMIN);
        boolean started = services.simulationService().start();
        return ok(MessageType.START_SIMULATION_RESPONSE,
                new BasicResponse(true, started ? "Symulacja została uruchomiona." : "Symulacja już działa."));
    }

    private ProtocolMessage stopSimulation(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        services.userService().requireRole(user, UserRole.DISPATCHER, UserRole.ADMIN);
        boolean stopped = services.simulationService().stop();
        return ok(MessageType.STOP_SIMULATION_RESPONSE,
                new BasicResponse(true, stopped ? "Symulacja została zatrzymana." : "Symulacja nie była uruchomiona."));
    }

    private ProtocolMessage simulationStatus(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        services.userService().requireRole(user, UserRole.DISPATCHER, UserRole.ADMIN);
        boolean running = services.simulationService().isRunning();
        return ok(MessageType.SIMULATION_STATUS_RESPONSE,
                new DataResponse<>(true, running ? "Symulacja jest uruchomiona." : "Symulacja jest zatrzymana.", running));
    }

    private ProtocolMessage listNotifications(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        return ok(MessageType.LIST_NOTIFICATIONS_RESPONSE,
                new DataResponse<>(true, "Powiadomienia pobrane.", services.notificationService().listForUser(user)));
    }

    private ProtocolMessage listNotificationRecipients(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        return ok(MessageType.LIST_NOTIFICATION_RECIPIENTS_RESPONSE,
                new DataResponse<>(true, "Odbiorcy pobrani.", services.notificationService().recipients(user)));
    }

    private ProtocolMessage sendNotification(ProtocolMessage message) {
        SendNotificationRequest request = ProtocolJson.fromJson(message.getPayload(), SendNotificationRequest.class);
        User user = requireUser(request.token());
        return ok(MessageType.SEND_NOTIFICATION_RESPONSE,
                new DataResponse<>(true, "Powiadomienie zostało wysłane.",
                        services.notificationService().send(user, request.recipientUserId(), request.title(), request.message())));
    }

    private ProtocolMessage markNotificationRead(ProtocolMessage message) {
        NotificationReadRequest request = ProtocolJson.fromJson(message.getPayload(), NotificationReadRequest.class);
        User user = requireUser(request.token());
        return ok(MessageType.MARK_NOTIFICATION_READ_RESPONSE,
                new DataResponse<>(true, "Powiadomienie oznaczono jako przeczytane.",
                        services.notificationService().markRead(user, request.notificationId())));
    }

    private ProtocolMessage markAllNotificationsRead(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        return ok(MessageType.MARK_ALL_NOTIFICATIONS_READ_RESPONSE,
                new DataResponse<>(true, "Wszystkie powiadomienia oznaczono jako przeczytane.",
                        services.notificationService().markAllRead(user)));
    }

    private ProtocolMessage listReports(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        services.userService().requireRole(user, UserRole.DISPATCHER, UserRole.ADMIN);
        return ok(MessageType.LIST_REPORTS_RESPONSE,
                new DataResponse<>(true, "Raporty pobrane.",
                        services.reportService().listReports(user.getRole() == UserRole.DISPATCHER)));
    }

    private ProtocolMessage generateReport(ProtocolMessage message) {
        TokenRequest request = ProtocolJson.fromJson(message.getPayload(), TokenRequest.class);
        User user = requireUser(request.token());
        services.userService().requireRole(user, UserRole.DISPATCHER, UserRole.ADMIN);
        String fileName = user.getRole() == UserRole.ADMIN
                ? services.reportService().generateManualReport(user.getFullName())
                : services.reportService().generateOperationalManualReport(user.getFullName());
        return ok(MessageType.GENERATE_REPORT_RESPONSE, new DataResponse<>(true, "Raport został wygenerowany.", fileName));
    }

    private ProtocolMessage readReport(ProtocolMessage message) {
        ReportRequest request = ProtocolJson.fromJson(message.getPayload(), ReportRequest.class);
        User user = requireUser(request.token());
        services.userService().requireRole(user, UserRole.DISPATCHER, UserRole.ADMIN);
        String content = user.getRole() == UserRole.DISPATCHER
                ? services.reportService().readOperationalReport(request.fileName())
                : services.reportService().readReport(request.fileName());
        return ok(MessageType.READ_REPORT_RESPONSE, new DataResponse<>(true, "Raport odczytany.", content));
    }

    private User requireUser(String token) {
        return services.sessionService().requireUser(token);
    }

    private ProtocolMessage ok(MessageType type, Object payload) {
        return ProtocolJson.ok(type, payload);
    }
}
