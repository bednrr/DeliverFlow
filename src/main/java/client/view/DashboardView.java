package client.view;

import client.map.AddressSuggestion;
import client.map.GeoCoordinate;
import client.map.GeocodingService;
import client.map.OsrmRouteService;
import client.controller.SessionContext;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import model.ClientAddress;
import model.Courier;
import model.CourierStatus;
import model.EventLog;
import model.Notification;
import model.Parcel;
import model.ParcelSize;
import model.ParcelStatus;
import model.ParcelStatusHistory;
import model.Theme;
import model.User;
import model.UserRole;
import protocol.MessageType;
import protocol.ProtocolMessage;
import protocol.requests.AccountUpdateRequest;
import protocol.requests.AddressRequest;
import protocol.requests.AssignCourierRequest;
import protocol.requests.CourierStatusRequest;
import protocol.requests.CreateParcelRequest;
import protocol.requests.IdRequest;
import protocol.requests.NotificationReadRequest;
import protocol.requests.ParcelStatusRequest;
import protocol.requests.ReportRequest;
import protocol.requests.SaveUserRequest;
import protocol.requests.SendNotificationRequest;
import protocol.requests.TokenRequest;
import service.AssignmentResult;
import util.AppConfig;
import util.PhoneUtil;
import util.PricingFormula;
import util.TimeUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

public class DashboardView {
    private static final DateTimeFormatter DASHBOARD_DATE_TIME = DateTimeFormatter.ofPattern("dd.MM, HH:mm");
    private static final DateTimeFormatter DASHBOARD_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SessionContext session;
    private final Runnable onLogout;
    private final Consumer<Theme> onThemeChange;
    private final AppConfig config = new AppConfig();
    private final GeocodingService geocodingService = new GeocodingService(config);
    private final OsrmRouteService osrmRouteService = new OsrmRouteService(config);
    private final Map<String, GeoCoordinate> priceGeocodeCache = new ConcurrentHashMap<>();
    private final Map<String, Double> priceRouteMinutesCache = new ConcurrentHashMap<>();
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final java.util.ArrayList<Button> menuButtons = new java.util.ArrayList<>();
    private DisposableContent activeDisposableContent;
    private Button activeMenuButton;
    private VBox rightSideBox;
    private Region notificationIndicator;
    private static final double SIDEBAR_MARGIN_LEFT = 14;
    private static final double SIDEBAR_WIDTH = 230;
    private static final double CONTENT_MARGIN = 14;
    private static final double CONTENT_PADDING = 18;
    private static final double DASHBOARD_REFERENCE_WIDTH = 980;
    private static final double DASHBOARD_REFERENCE_HEIGHT = 650;
    private static final double DASHBOARD_MIN_SCALE = 0.5;
    private static final double DIALOG_SCREEN_MARGIN = 72;
    private static final double PARCEL_SUCCESS_VALUE_MAX_WIDTH = 170;

    public DashboardView(SessionContext session, Runnable onLogout, Consumer<Theme> onThemeChange) {
        this.session = session;
        this.onLogout = onLogout;
        this.onThemeChange = onThemeChange;
        build();
    }

    public Parent root() {
        return root;
    }

    private void build() {
        VBox sidebar = buildSidebar();
        rightSideBox = new VBox(0);
        rightSideBox.getStyleClass().add("content-container");
        HBox headerBar = buildHeader();
        rightSideBox.getChildren().addAll(headerBar, content);
        VBox.setVgrow(content, Priority.ALWAYS);

        StackPane rightWrapper = new StackPane(rightSideBox);
        rightWrapper.getStyleClass().add("main-area");
        rightWrapper.setPadding(new Insets(14, 14, 14, 0));
        HBox.setHgrow(rightWrapper, Priority.ALWAYS);

        HBox layout = new HBox(0, sidebar, rightWrapper);
        root.setCenter(layout);
        showStart();
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(6);
        sidebar.setPadding(new Insets(20, 18, 24, 18));
        sidebar.setPrefWidth(252);
        sidebar.setMinWidth(252);
        sidebar.setMaxWidth(252);
        sidebar.getStyleClass().add("sidebar");

        StackPane logoSlot = LogoAssets.sidebarLogo();
        logoSlot.setPrefSize(212, 66);
        logoSlot.setMinSize(212, 66);
        logoSlot.setMaxSize(212, 66);
        VBox.setMargin(logoSlot, new Insets(8, 0, 38, 0));
        sidebar.getChildren().add(logoSlot);

        menuButtons.clear();
        UserRole role = session.user().getRole();

        Button dashboardBtn = menuButton("Panel główny", this::showDashboard);
        Button settingsBtn = menuButton("Ustawienia", this::showSettings);

        if (role == UserRole.CLIENT) {
            sidebar.getChildren().addAll(dashboardBtn,
                    menuButton("Moje paczki", this::showClientParcels),
                    menuButton("Nadaj paczkę", this::showCreateParcel),
                    menuButton("Moje adresy", this::showAddresses),
                    settingsBtn);
        }
        if (role == UserRole.COURIER) {
            sidebar.getChildren().addAll(dashboardBtn,
                    menuButton("Moje zlecenia", this::showCourierParcels),
                    menuButton("Mapa", this::showMap),
                    settingsBtn);
        }
        if (role == UserRole.DISPATCHER) {
            sidebar.getChildren().addAll(dashboardBtn,
                    menuButton("Paczki i przypisania", this::showDispatcherParcels),
                    menuButton("Kurierzy", this::showCourierList),
                    menuButton("Mapa", this::showMap),
                    menuButton("Raport zdarzeń", this::showEvents),
                    settingsBtn);
        }
        if (role == UserRole.ADMIN) {
            sidebar.getChildren().addAll(dashboardBtn,
                    menuButton("Użytkownicy", this::showAdminUsers),
                    menuButton("Raporty", this::showReports),
                    settingsBtn);
        }

        Region logoutGap = new Region();
        logoutGap.setMinHeight(24);
        VBox.setVgrow(logoutGap, Priority.ALWAYS);
        sidebar.getChildren().add(logoutGap);

        if (role == UserRole.COURIER) {
            VBox statusBox = courierStatusBox();
            VBox.setMargin(statusBox, new Insets(0, 0, 16, 0));
            sidebar.getChildren().add(statusBox);
        }

        Button logout = new Button("Wyloguj");
        logout.getStyleClass().add("side-menu-logout");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(event -> { disposeActiveContent(); session.logout(); onLogout.run(); });
        VBox.setMargin(logout, new Insets(0, 0, 28, 0));
        sidebar.getChildren().add(logout);
        return sidebar;
    }

    private HBox buildHeader() {
        HBox box = new HBox(16);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(26, 30, 14, 30));
        box.getStyleClass().add("top-header");

        String initials = initials(session.user().getFirstName(), session.user().getLastName());
        Label avatarLabel = new Label(initials);
        avatarLabel.getStyleClass().add("avatar-circle");

        VBox userInfo = new VBox(3);
        Label userName = new Label(session.user().getFullName());
        userName.getStyleClass().add("header-user");
        Label userRole = new Label(session.user().getRole().displayName());
        userRole.getStyleClass().add("header-role");
        userInfo.getChildren().addAll(userName, userRole);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button bell = new Button("\uD83D\uDD14");
        bell.getStyleClass().clear();
        bell.getStyleClass().add("notification-bell");
        bell.setOnAction(event -> showNotificationsDialog());
        notificationIndicator = new Region();
        notificationIndicator.getStyleClass().add("notification-new-indicator");
        notificationIndicator.setVisible(false);
        notificationIndicator.setManaged(false);
        StackPane bellWrap = new StackPane(bell, notificationIndicator);
        StackPane.setAlignment(notificationIndicator, Pos.TOP_RIGHT);
        refreshNotificationIndicator();

        box.getChildren().addAll(avatarLabel, userInfo, spacer, bellWrap);
        return box;
    }

    private void showNotificationsDialog() {
        ObservableList<Notification> notifications = FXCollections.observableArrayList();
        ListView<Notification> list = new ListView<>(notifications);
        list.setPlaceholder(new Label("Brak nowych powiadomień."));
        list.setCellFactory(view -> notificationCell());
        list.getStyleClass().add("notification-list");
        boolean readOnlyNotifications = session.user().getRole() == UserRole.CLIENT;
        list.setPrefHeight(readOnlyNotifications ? 420 : 300);

        Button markAllRead = new Button("Oznacz wszystkie jako przeczytane");
        Button markRead = new Button("Oznacz jako przeczytane");
        Button refresh = new Button("Odśwież");
        Button close = new Button("OK");
        markAllRead.getStyleClass().add("secondary-button");
        refresh.getStyleClass().add("compact-button");
        markRead.getStyleClass().add("secondary-button");
        close.getStyleClass().add("compact-button");
        refresh.setOnAction(event -> loadNotifications(notifications));
        markAllRead.setOnAction(event -> markAllNotificationsAsRead(notifications));
        markRead.setOnAction(event -> markSelectedNotificationAsRead(list, notifications));
        Region listActionSpacer = new Region();
        HBox.setHgrow(listActionSpacer, Priority.ALWAYS);
        HBox listActions = new HBox(8, markAllRead, markRead, refresh, listActionSpacer, close);
        listActions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, list, listActions);
        content.getStyleClass().add("notifications-dialog-content");

        if (!readOnlyNotifications) {
            content.getChildren().add(notificationComposer(notifications));
        }

        loadNotifications(notifications);
        Dialog<Void> dialog = new Dialog<>();
        ButtonType closeType = new ButtonType("Zamknij", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeType);
        dialog.setTitle("DeliverFlow");
        dialog.setHeaderText("Twoje powiadomienia");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(900, readOnlyNotifications ? 620 : 760);
        dialog.getDialogPane().setMinWidth(0);
        dialog.getDialogPane().getStyleClass().add("dialog-info");
        dialog.getDialogPane().getStyleClass().add("notifications-dialog");
        styleDialog(dialog);
        Node hiddenCloseButton = dialog.getDialogPane().lookupButton(closeType);
        if (hiddenCloseButton != null) {
            hiddenCloseButton.setVisible(false);
            hiddenCloseButton.setManaged(false);
        }
        close.setOnAction(event -> dialog.close());
        dialog.showAndWait();
        refreshNotificationIndicator();
    }

    private ListCell<Notification> notificationCell() {
        return new ListCell<>() {
            private final Label title = new Label();
            private final Label meta = new Label();
            private final Label message = new Label();
            private final VBox content = new VBox(4, title, meta, message);

            {
                title.getStyleClass().add("notification-title");
                meta.getStyleClass().add("notification-meta");
                message.getStyleClass().add("notification-message");
                message.setWrapText(true);
                content.getStyleClass().add("notification-cell-content");
            }

            @Override
            protected void updateItem(Notification item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("notification-unread-cell");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                title.setText(nullToEmpty(item.getTitle()));
                meta.setText("Od: " + nullToEmpty(item.getSenderName()) + " • "
                        + TimeUtil.formatDisplay(item.getCreatedAt())
                        + (item.isRead() ? " • przeczytane" : " • nowe"));
                message.setText(nullToEmpty(item.getMessage()));
                if (!item.isRead()) {
                    getStyleClass().add("notification-unread-cell");
                }
                setText(null);
                setGraphic(content);
            }
        };
    }

    private VBox notificationComposer(ObservableList<Notification> notifications) {
        ObservableList<User> recipients = FXCollections.observableArrayList();
        ComboBox<User> recipient = new ComboBox<>(recipients);
        recipient.setPromptText("Wybierz odbiorcę");
        recipient.setPrefWidth(300);
        stabilizeComboBox(recipient);
        TextField title = new TextField();
        title.setPromptText("Tytuł powiadomienia");
        TextArea message = new TextArea();
        message.setPromptText("Treść wiadomości");
        message.setWrapText(true);
        message.setPrefRowCount(3);
        Button sendNotification = new Button("Wyślij powiadomienie");
        sendNotification.getStyleClass().add("primary-action-button");
        sendNotification.setOnAction(event -> {
            User selectedRecipient = recipient.getValue();
            if (selectedRecipient == null) {
                UiDialogs.error("Wybierz odbiorcę powiadomienia.");
                return;
            }
            SendNotificationRequest request = new SendNotificationRequest(session.token(), selectedRecipient.getId(),
                    title.getText(), message.getText());
            send(MessageType.SEND_NOTIFICATION_REQUEST, request, response -> {
                title.clear();
                message.clear();
                UiDialogs.info("Powiadomienie wysłane", session.apiClient().responseMessage(response));
                if (notifications != null) {
                    loadNotifications(notifications);
                }
            });
        });

        send(MessageType.LIST_NOTIFICATION_RECIPIENTS_REQUEST, new TokenRequest(session.token()),
                response -> recipients.setAll(session.apiClient().dataList(response, User.class)));

        HBox topRow = new HBox(10, singleField("Odbiorca", recipient), singleField("Tytuł", title));
        HBox.setHgrow(topRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(topRow.getChildren().get(1), Priority.ALWAYS);
        Label heading = new Label("Nowe powiadomienie");
        heading.getStyleClass().add("subsection-title");
        VBox composer = new VBox(10, heading, topRow, singleField("Treść", message), sendNotification);
        composer.getStyleClass().add("form-section");
        return composer;
    }

    private void loadNotifications(ObservableList<Notification> notifications) {
        send(MessageType.LIST_NOTIFICATIONS_REQUEST, new TokenRequest(session.token()),
                response -> {
                    notifications.setAll(session.apiClient().dataList(response, Notification.class));
                    refreshNotificationIndicatorFrom(notifications);
                });
    }

    private void markSelectedNotificationAsRead(ListView<Notification> list, ObservableList<Notification> notifications) {
        Notification selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiDialogs.error("Wybierz powiadomienie.");
            return;
        }
        int index = list.getSelectionModel().getSelectedIndex();
        send(MessageType.MARK_NOTIFICATION_READ_REQUEST, new NotificationReadRequest(session.token(), selected.getId()), response -> {
            Notification updated = session.apiClient().data(response, Notification.class);
            if (index >= 0 && index < notifications.size()) {
                notifications.set(index, updated);
                list.getSelectionModel().select(index);
            } else {
                loadNotifications(notifications);
            }
            refreshNotificationIndicatorFrom(notifications);
        });
    }

    private void markAllNotificationsAsRead(ObservableList<Notification> notifications) {
        send(MessageType.MARK_ALL_NOTIFICATIONS_READ_REQUEST, new TokenRequest(session.token()), response -> {
            notifications.setAll(session.apiClient().dataList(response, Notification.class));
            refreshNotificationIndicatorFrom(notifications);
        });
    }

    private void refreshNotificationIndicator() {
        if (notificationIndicator == null) {
            return;
        }
        send(MessageType.LIST_NOTIFICATIONS_REQUEST, new TokenRequest(session.token()),
                response -> refreshNotificationIndicatorFrom(session.apiClient().dataList(response, Notification.class)));
    }

    private void refreshNotificationIndicatorFrom(List<Notification> notifications) {
        if (notificationIndicator == null) {
            return;
        }
        boolean hasUnread = notifications != null && notifications.stream().anyMatch(notification -> !notification.isRead());
        notificationIndicator.setVisible(hasUnread);
        notificationIndicator.setManaged(hasUnread);
    }

    private String initials(String firstName, String lastName) {
        String f = (firstName == null || firstName.isBlank()) ? "" : firstName.substring(0, 1).toUpperCase(Locale.ROOT);
        String l = (lastName == null || lastName.isBlank()) ? "" : lastName.substring(0, 1).toUpperCase(Locale.ROOT);
        return f + l;
    }

    private Button menuButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("side-menu-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> {
            activateMenuButton(button);
            action.run();
        });
        menuButtons.add(button);
        return button;
    }

    private void activateMenuButton(Button button) {
        if (activeMenuButton != null) {
            activeMenuButton.getStyleClass().remove("side-menu-button-active");
            if (!activeMenuButton.getStyleClass().contains("side-menu-button")) {
                activeMenuButton.getStyleClass().add("side-menu-button");
            }
        }
        activeMenuButton = button;
        button.getStyleClass().remove("side-menu-button");
        if (!button.getStyleClass().contains("side-menu-button-active")) {
            button.getStyleClass().add("side-menu-button-active");
        }
    }

    private VBox courierStatusBox() {
        Label label = new Label("Dostępność");
        label.getStyleClass().add("sidebar-section-title");
        ComboBox<CourierStatus> status = new ComboBox<>(FXCollections.observableArrayList(CourierStatus.values()));
        status.getSelectionModel().select(CourierStatus.AVAILABLE);
        stabilizeComboBox(status);
        Button save = new Button("Zapisz status");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> send(MessageType.UPDATE_COURIER_STATUS_REQUEST,
                new CourierStatusRequest(session.token(), 0, status.getValue()),
                response -> UiDialogs.info(session.apiClient().responseMessage(response))));

        send(MessageType.LIST_COURIERS_REQUEST, new TokenRequest(session.token()), response ->
                session.apiClient().dataList(response, Courier.class).stream()
                        .filter(courier -> courier.getUserId() == session.user().getId())
                        .findFirst()
                        .ifPresent(courier -> status.setValue(courier.getStatus())));

        VBox box = new VBox(8, label, status, save);
        box.getStyleClass().add("sidebar-status");
        return box;
    }

    private void showStart() {
        if (!menuButtons.isEmpty()) {
            activateMenuButton(menuButtons.getFirst());
        }
        showDashboard();
    }

    private void showDashboard() {
        UserRole role = session.user().getRole();

        VBox page = new VBox(0);
        page.setPadding(new Insets(0));
        page.getStyleClass().add("dashboard-page");

        if (role == UserRole.CLIENT) {
            loadClientDashboard(page);
        } else if (role == UserRole.COURIER) {
            loadCourierDashboard(page);
        } else if (role == UserRole.DISPATCHER) {
            loadDispatcherDashboard(page);
        } else if (role == UserRole.ADMIN) {
            loadAdminDashboard(page);
        }

        Pane scaledPage = new Pane(page);
        scaledPage.getStyleClass().add("dashboard-scale-pane");
        StackPane dashboardContent = new StackPane(scaledPage);
        dashboardContent.getStyleClass().add("dashboard-scale-content");
        ScrollPane dashboardViewport = new ScrollPane(dashboardContent);
        dashboardViewport.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        dashboardViewport.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        dashboardViewport.setPannable(false);
        dashboardViewport.getStyleClass().add("dashboard-scale-viewport");
        configureDashboardScaling(dashboardViewport, dashboardContent, scaledPage, page);
        setContent(dashboardViewport);
    }

    private void configureDashboardScaling(ScrollPane viewport, StackPane dashboardContent, Pane scaledPage, VBox page) {
        dashboardContent.setAlignment(Pos.CENTER);
        scaledPage.setMinSize(0, 0);
        page.setFillWidth(true);
        if (page.getChildren().isEmpty() || !(page.getChildren().getFirst() instanceof Region gridRegion)) {
            return;
        }

        gridRegion.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(gridRegion, Priority.ALWAYS);
        Runnable updateScale = () -> {
            double viewportWidth = viewport.getViewportBounds().getWidth();
            double viewportHeight = viewport.getViewportBounds().getHeight();
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                return;
            }

            Insets pageInsets = page.getInsets() == null ? Insets.EMPTY : page.getInsets();
            double pageVerticalPadding = pageInsets.getTop() + pageInsets.getBottom();
            double scale = Math.min(1.0, Math.min(
                    viewportWidth / DASHBOARD_REFERENCE_WIDTH,
                    viewportHeight / DASHBOARD_REFERENCE_HEIGHT));
            scale = Math.max(DASHBOARD_MIN_SCALE, scale);

            double targetPageWidth = Math.max(DASHBOARD_REFERENCE_WIDTH, viewportWidth / scale);
            double targetPageHeight = Math.max(DASHBOARD_REFERENCE_HEIGHT + pageVerticalPadding, viewportHeight / scale);
            double targetGridHeight = Math.max(0, targetPageHeight - pageVerticalPadding);
            double scaledWidth = targetPageWidth * scale;
            double scaledHeight = targetPageHeight * scale;

            page.setScaleX(scale);
            page.setScaleY(scale);
            page.setTranslateX(-targetPageWidth * (1 - scale) / 2);
            page.setTranslateY(-targetPageHeight * (1 - scale) / 2);
            page.setMinWidth(targetPageWidth);
            page.setPrefWidth(targetPageWidth);
            page.setMaxWidth(targetPageWidth);
            page.setMinHeight(targetPageHeight);
            page.setPrefHeight(targetPageHeight);
            page.setMaxHeight(targetPageHeight);
            gridRegion.setMinHeight(targetGridHeight);
            gridRegion.setPrefHeight(targetGridHeight);
            gridRegion.setMaxHeight(targetGridHeight);
            scaledPage.setMinSize(scaledWidth, scaledHeight);
            scaledPage.setPrefSize(scaledWidth, scaledHeight);
            scaledPage.setMaxSize(scaledWidth, scaledHeight);
            dashboardContent.setMinSize(Math.max(viewportWidth, scaledWidth), Math.max(viewportHeight, scaledHeight));
            dashboardContent.setPrefSize(Math.max(viewportWidth, scaledWidth), Math.max(viewportHeight, scaledHeight));
        };

        viewport.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(updateScale));
        Platform.runLater(updateScale);
    }

    private GridPane dashboardThreeColumnGrid(double leftPercent, double centerPercent, double rightPercent) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("dashboard-grid");
        grid.setMaxHeight(Double.MAX_VALUE);

        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(leftPercent);
        left.setHgrow(Priority.ALWAYS);
        ColumnConstraints center = new ColumnConstraints();
        center.setPercentWidth(centerPercent);
        center.setHgrow(Priority.ALWAYS);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(rightPercent);
        right.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(left, center, right);
        configureDashboardRows(grid, 38, 34, 28);
        return grid;
    }

    private void configureDashboardRows(GridPane grid, double... rowPercents) {
        grid.getRowConstraints().clear();
        for (double rowPercent : rowPercents) {
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(rowPercent);
            row.setVgrow(Priority.ALWAYS);
            row.setFillHeight(true);
            grid.getRowConstraints().add(row);
        }
    }

    private void placeDashboardCard(GridPane grid, javafx.scene.Node node, int column, int row) {
        placeDashboardCard(grid, node, column, row, 1, 1);
    }

    private void placeDashboardCard(GridPane grid, javafx.scene.Node node, int column, int row, int columnSpan, int rowSpan) {
        if (node instanceof Region region) {
            region.setMinWidth(0);
            region.setPrefWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMinHeight(0);
            region.setMaxHeight(Double.MAX_VALUE);
        }
        GridPane.setHgrow(node, Priority.ALWAYS);
        GridPane.setVgrow(node, Priority.ALWAYS);
        grid.add(node, column, row, columnSpan, rowSpan);
    }

    private VBox dashboardFactStackCard(String title, VBox... facts) {
        VBox stack = new VBox(10);
        stack.getStyleClass().add("dashboard-fact-stack");
        stack.setFillWidth(true);
        stack.setMinHeight(0);
        stack.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(stack, Priority.ALWAYS);
        for (VBox fact : facts) {
            fact.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(fact, Priority.ALWAYS);
        }
        stack.getChildren().addAll(facts);
        VBox card = dashboardSectionCard(title, stack);
        card.getStyleClass().add("dashboard-fact-stack-card");
        return card;
    }

    private VBox dashboardMiniFact(String valueText, String labelText) {
        Label value = new Label(valueText);
        value.getStyleClass().add("dashboard-mini-fact-value");
        value.setMinHeight(Region.USE_PREF_SIZE);
        Label label = new Label(labelText);
        label.getStyleClass().add("dashboard-mini-fact-label");
        label.setMinHeight(Region.USE_PREF_SIZE);
        VBox box = new VBox(4, value, label);
        box.getStyleClass().add("dashboard-mini-fact");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setFillWidth(true);
        box.setMinHeight(0);
        box.setMaxHeight(Double.MAX_VALUE);
        box.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(box, Priority.ALWAYS);
        box.getProperties().put("factValue", value);
        return box;
    }

    private VBox dashboardMiniFactInline(String valueText, String labelText) {
        Label value = new Label(valueText);
        value.getStyleClass().add("dashboard-mini-fact-value");
        value.setMinHeight(Region.USE_PREF_SIZE);
        Label label = new Label(labelText);
        label.getStyleClass().add("dashboard-mini-fact-label");
        label.getStyleClass().add("dashboard-mini-fact-label-inline");
        label.setMinHeight(Region.USE_PREF_SIZE);
        HBox row = new HBox(10, value, label);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(row);
        box.getStyleClass().add("dashboard-mini-fact");
        box.getStyleClass().add("dashboard-mini-fact-inline");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setFillWidth(true);
        box.setMinHeight(0);
        box.setMaxHeight(Double.MAX_VALUE);
        box.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(box, Priority.ALWAYS);
        box.getProperties().put("factValue", value);
        return box;
    }

    private VBox dashboardBarChartCard(String title, List<String> labels, List<Long> values) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dashboard-card-title");

        HBox bars = new HBox(16);
        bars.setAlignment(Pos.BOTTOM_CENTER);
        bars.getStyleClass().add("dashboard-bar-chart");

        long max = Math.max(1L, values.stream().mapToLong(Long::longValue).max().orElse(1L));
        int highlightIndex = values.isEmpty() ? -1 : indexOfMax(values);

        for (int index = 0; index < labels.size(); index++) {
            long value = index < values.size() ? values.get(index) : 0L;
            Label valueLabel = new Label(String.valueOf(value));
            valueLabel.getStyleClass().add("dashboard-bar-value");

            Region bar = new Region();
            bar.getStyleClass().add(index == highlightIndex ? "dashboard-bar-highlight" : "dashboard-bar");
            double normalized = max <= 0 ? 0.15 : (double) value / (double) max;
            double height = 28 + normalized * 92;
            bar.setMinWidth(22);
            bar.setPrefWidth(22);
            bar.setMaxWidth(22);
            bar.setMinHeight(height);
            bar.setPrefHeight(height);
            bar.setMaxHeight(height);

            Label label = new Label(index < labels.size() ? labels.get(index) : "");
            label.getStyleClass().add("dashboard-bar-label");

            VBox column = new VBox(8, valueLabel, bar, label);
            column.setAlignment(Pos.BOTTOM_CENTER);
            column.getStyleClass().add("dashboard-bar-column");
            bars.getChildren().add(column);
        }

        StackPane chartWrap = new StackPane(bars);
        chartWrap.getStyleClass().add("dashboard-chart-wrap");
        chartWrap.setAlignment(Pos.CENTER);
        chartWrap.setMinHeight(0);
        chartWrap.setMaxHeight(Double.MAX_VALUE);

        VBox card = new VBox(12, titleLabel, chartWrap);
        card.getStyleClass().addAll("dashboard-section-card", "dashboard-chart-card");
        card.setFillWidth(true);
        card.setMinHeight(0);
        card.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(chartWrap, Priority.ALWAYS);
        return card;
    }

    private int indexOfMax(List<Long> values) {
        int index = 0;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) >= max) {
                max = values.get(i);
                index = i;
            }
        }
        return index;
    }

    private List<LocalDate> lastDays(int count) {
        List<LocalDate> days = new ArrayList<>(count);
        LocalDate start = LocalDate.now().minusDays(Math.max(0, count - 1));
        for (int i = 0; i < count; i++) {
            days.add(start.plusDays(i));
        }
        return days;
    }

    private List<String> dayLabels(List<LocalDate> days) {
        return days.stream()
                .map(day -> day.format(DateTimeFormatter.ofPattern("dd.MM")))
                .toList();
    }

    private List<Long> parcelCountsByCreatedDay(List<Parcel> parcels, List<LocalDate> days) {
        return days.stream()
                .map(day -> parcels.stream()
                        .filter(parcel -> parcel.getCreatedAt() != null && parcel.getCreatedAt().toLocalDate().equals(day))
                        .count())
                .toList();
    }

    private List<Long> deliveredParcelCountsByDay(List<Parcel> parcels, List<LocalDate> days) {
        return days.stream()
                .map(day -> parcels.stream()
                        .filter(parcel -> parcel.getStatus() == ParcelStatus.DELIVERED)
                        .filter(parcel -> parcel.getUpdatedAt() != null && parcel.getUpdatedAt().toLocalDate().equals(day))
                        .count())
                .toList();
    }

    private List<Long> clientRegistrationCountsByDay(List<User> users, List<LocalDate> days) {
        return days.stream()
                .map(day -> users.stream()
                        .filter(user -> user.getRole() == UserRole.CLIENT)
                        .filter(user -> user.getCreatedAt() != null && user.getCreatedAt().toLocalDate().equals(day))
                        .count())
                .toList();
    }

    private String formatTenure(LocalDateTime createdAt) {
        if (createdAt == null) {
            return "Brak danych";
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDate.now());
        if (days <= 0) {
            return "Od dziś";
        }
        if (days == 1) {
            return "1 dzień";
        }
        return days + " dni";
    }

    private List<Parcel> parcelsForCourier(List<Parcel> parcels, long courierId) {
        return parcels.stream()
                .filter(parcel -> Objects.equals(parcel.getAssignedCourierId(), courierId))
                .toList();
    }

    private VBox dashboardSectionCard(String title, javafx.scene.Node... nodes) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dashboard-card-title");
        titleLabel.setMinHeight(Region.USE_PREF_SIZE);

        VBox card = new VBox(12);
        card.getStyleClass().add("dashboard-section-card");
        card.setFillWidth(true);
        card.setMinHeight(0);
        card.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(card, Priority.ALWAYS);
        for (javafx.scene.Node node : nodes) {
            if (node instanceof Region region) {
                region.setMinWidth(0);
                region.setMaxWidth(Double.MAX_VALUE);
                if (!region.getStyleClass().contains("dashboard-progress-row")) {
                    region.setMinHeight(0);
                    region.setMaxHeight(Double.MAX_VALUE);
                }
            }
        }
        card.getChildren().add(titleLabel);
        card.getChildren().addAll(nodes);
        return card;
    }

    private VBox dashboardProgressRow(String labelText) {
        Label label = new Label(labelText);
        label.getStyleClass().add("dashboard-progress-label");
        Label value = new Label("...");
        value.getStyleClass().add("dashboard-progress-value");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, label, spacer, value);
        header.setAlignment(Pos.CENTER_LEFT);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setMinHeight(8);
        progressBar.setPrefHeight(8);
        progressBar.getStyleClass().add("dashboard-progress-bar");

        VBox row = new VBox(6, header, progressBar);
        row.setFillWidth(true);
        row.setMinHeight(Region.USE_PREF_SIZE);
        row.getStyleClass().add("dashboard-progress-row");
        row.getProperties().put("progressValue", value);
        row.getProperties().put("progressBar", progressBar);
        return row;
    }

    private VBox dashboardListContainer(String emptyText) {
        VBox container = new VBox(10);
        container.getStyleClass().add("dashboard-list");
        container.setFillWidth(true);
        container.setMinHeight(0);
        container.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(container, Priority.ALWAYS);
        populateDashboardList(container, List.of(), emptyText);
        return container;
    }

    private HBox dashboardListItem(String title, String meta, String chipText, String chipVariant) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dashboard-list-title");
        titleLabel.setWrapText(true);

        Label metaLabel = new Label(meta);
        metaLabel.getStyleClass().add("dashboard-list-meta");
        metaLabel.setWrapText(true);

        VBox textBox = new VBox(4, titleLabel, metaLabel);
        textBox.setMinWidth(0);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label chip = new Label(chipText);
        chip.getStyleClass().addAll("dashboard-chip", "dashboard-chip-" + chipVariant);
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chip.setMaxWidth(Region.USE_PREF_SIZE);
        chip.setAlignment(Pos.CENTER);

        HBox row = new HBox(12, textBox, chip);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("dashboard-list-item");
        return row;
    }

    private VBox dashboardFactTile(String labelText) {
        Label valueLabel = new Label("...");
        valueLabel.getStyleClass().add("dashboard-fact-value");
        Label labelLabel = new Label(labelText);
        labelLabel.getStyleClass().add("dashboard-fact-label");
        VBox tile = new VBox(8, valueLabel, labelLabel);
        tile.getStyleClass().add("dashboard-fact-tile");
        tile.setMinHeight(0);
        tile.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.getProperties().put("factValue", valueLabel);
        return tile;
    }

    private HBox dashboardFactsRow(VBox... facts) {
        HBox row = new HBox(14, facts);
        row.getStyleClass().add("dashboard-facts-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(0);
        row.setMaxWidth(Double.MAX_VALUE);
        for (VBox fact : facts) {
            HBox.setHgrow(fact, Priority.ALWAYS);
        }
        return row;
    }

    private void updateDashboardProgress(VBox row, long value, long total) {
        Label valueLabel = (Label) row.getProperties().get("progressValue");
        ProgressBar progressBar = (ProgressBar) row.getProperties().get("progressBar");
        if (valueLabel != null) {
            valueLabel.setText(String.valueOf(value));
        }
        if (progressBar != null) {
            progressBar.setProgress(total <= 0 ? 0 : (double) value / (double) total);
        }
    }

    private void populateDashboardList(VBox container, List<HBox> rows, String emptyText) {
        if (rows == null || rows.isEmpty()) {
            Label empty = new Label(emptyText);
            empty.getStyleClass().add("dashboard-empty");
            container.getChildren().setAll(empty);
            return;
        }
        container.getChildren().setAll(rows);
    }

    private void updateFactTile(VBox tile, String value) {
        Label valueLabel = (Label) tile.getProperties().get("factValue");
        if (valueLabel != null) {
            valueLabel.setText(value);
        }
    }

    private String parcelChipVariant(ParcelStatus status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case DELIVERED -> "success";
            case DELIVERY_PROBLEM, CANCELED -> "danger";
            case WAITING_FOR_COURIER, WAREHOUSE -> "warning";
            case PICKUP_IN_PROGRESS, IN_TRANSIT, OUT_FOR_DELIVERY -> "accent";
        };
    }

    private String courierChipVariant(CourierStatus status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case AVAILABLE -> "success";
            case BUSY -> "accent";
            case BREAK -> "warning";
            case OFFLINE -> "neutral";
        };
    }

    private String eventLevelVariant(String level) {
        if (level == null) {
            return "neutral";
        }
        String normalized = level.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ERROR", "SEVERE" -> "danger";
            case "WARN", "WARNING" -> "warning";
            case "INFO" -> "accent";
            default -> "neutral";
        };
    }

    private String dashboardEventMessage(EventLog event) {
        String message = safeText(event == null ? null : event.getMessage(), "Brak komunikatu");
        return shorten(message, 84);
    }

    private static int parcelPriority(Parcel parcel) {
        if (parcel == null || parcel.getStatus() == null) {
            return 99;
        }
        return switch (parcel.getStatus()) {
            case DELIVERY_PROBLEM -> 0;
            case WAITING_FOR_COURIER -> 1;
            case PICKUP_IN_PROGRESS -> 2;
            case IN_TRANSIT -> 3;
            case WAREHOUSE -> 4;
            case OUT_FOR_DELIVERY -> 5;
            case DELIVERED -> 6;
            case CANCELED -> 7;
        };
    }

    private String formatCurrency(BigDecimal amount) {
        return formatCurrencyValue(amount) + " zł";
    }

    private String formatCurrencyValue(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatWeight(double weightKg) {
        BigDecimal value = BigDecimal.valueOf(weightKg).stripTrailingZeros();
        String text = value.scale() < 0 ? value.setScale(0).toPlainString() : value.toPlainString();
        return text + " kg";
    }

    private String activeParcelDestination(Parcel parcel) {
        if (parcel == null || parcel.getStatus() == null) {
            return "Brak";
        }
        return switch (parcel.getStatus()) {
            case WAITING_FOR_COURIER, PICKUP_IN_PROGRESS, WAREHOUSE ->
                    safeText(parcel.getSenderMapPointName(), safeText(parcel.getSenderAddressText(), "Nadawca"));
            case IN_TRANSIT, OUT_FOR_DELIVERY ->
                    safeText(parcel.getReceiverMapPointName(), safeText(parcel.getReceiverAddressText(), "Odbiorca"));
            case DELIVERED, CANCELED, DELIVERY_PROBLEM -> "Brak";
        };
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "Brak daty";
        }
        if (dateTime.toLocalDate().equals(LocalDate.now())) {
            return "Dziś, " + dateTime.toLocalTime().withSecond(0).withNano(0);
        }
        return dateTime.format(DASHBOARD_DATE_TIME);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return safeText(value, "");
        }
        return value.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private void loadClientDashboard(VBox page) {
        List<LocalDate> days = lastDays(5);
        VBox chartCard = dashboardBarChartCard("Wysłane przesyłki",
                dayLabels(days),
                List.of(0L, 0L, 0L, 0L, 0L));

        VBox sentFact = dashboardMiniFact("...", "Paczki wysłane");
        VBox valueFact = dashboardMiniFact("...", "Łączna wartość");
        VBox activeFact = dashboardMiniFact("...", "Aktywne teraz");
        VBox summaryCard = dashboardFactStackCard("Najważniejsze liczby",
                sentFact, valueFact, activeFact);

        VBox recentList = dashboardListContainer("Brak ostatnich paczek do wyświetlenia.");
        VBox recentCard = dashboardSectionCard("Ostatnie paczki",
                recentList);

        VBox waitingFact = dashboardMiniFact("...", "Oczekujące");
        VBox transitFact = dashboardMiniFact("...", "W drodze");
        VBox warehouseFact = dashboardMiniFact("...", "W centrali");
        VBox activeStatusCard = dashboardFactStackCard("Bieżące statusy",
                waitingFact, transitFact, warehouseFact);

        VBox smallFact = dashboardFactTile("Małe");
        VBox mediumFact = dashboardFactTile("Średnie");
        VBox largeFact = dashboardFactTile("Duże");
        VBox sizeCard = dashboardSectionCard("Typy przesyłek",
                dashboardFactsRow(smallFact, mediumFact, largeFact));

        VBox averageFact = dashboardFactTile("Średnia wartość");
        VBox deliveredFact = dashboardFactTile("Doręczone");
        VBox latestFact = dashboardFactTile("Ostatnia wysyłka");
        VBox insightsCard = dashboardSectionCard("Szybkie podsumowanie",
                dashboardFactsRow(averageFact, deliveredFact, latestFact));

        GridPane layout = dashboardThreeColumnGrid(36, 32, 32);
        placeDashboardCard(layout, chartCard, 0, 0, 2, 1);
        placeDashboardCard(layout, summaryCard, 2, 0);
        placeDashboardCard(layout, activeStatusCard, 0, 1);
        placeDashboardCard(layout, recentCard, 1, 1, 2, 1);
        placeDashboardCard(layout, insightsCard, 0, 2, 2, 1);
        placeDashboardCard(layout, sizeCard, 2, 2);
        page.getChildren().add(layout);

        send(MessageType.LIST_PARCELS_REQUEST, new TokenRequest(session.token()), response -> {
            List<Parcel> parcels = session.apiClient().dataList(response, Parcel.class);
            Parcel latestParcel = parcels.stream()
                    .sorted(Comparator.comparing(Parcel::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .findFirst()
                    .orElse(null);
            long delivered = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.DELIVERED).count();
            long waiting = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.WAITING_FOR_COURIER).count();
            long inTransit = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.IN_TRANSIT
                    || p.getStatus() == ParcelStatus.OUT_FOR_DELIVERY).count();
            long warehouse = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.WAREHOUSE).count();
            long active = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.WAITING_FOR_COURIER
                    || p.getStatus() == ParcelStatus.PICKUP_IN_PROGRESS
                    || p.getStatus() == ParcelStatus.IN_TRANSIT
                    || p.getStatus() == ParcelStatus.WAREHOUSE
                    || p.getStatus() == ParcelStatus.OUT_FOR_DELIVERY).count();
            java.math.BigDecimal total = parcels.stream().map(Parcel::getEstimatedPrice)
                    .filter(Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            updateFactTile(sentFact, String.valueOf(parcels.size()));
            updateFactTile(valueFact, formatCurrency(total));
            updateFactTile(activeFact, String.valueOf(active));
            updateFactTile(waitingFact, String.valueOf(waiting));
            updateFactTile(transitFact, String.valueOf(inTransit));
            updateFactTile(warehouseFact, String.valueOf(warehouse));

            populateDashboardList(recentList,
                    parcels.stream()
                            .sorted(Comparator.comparing(Parcel::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                            .limit(3)
                            .map(parcel -> dashboardListItem(
                                    safeText(parcel.getTitle(), "Bez tytułu"),
                                    safeText(parcel.getReceiverName(), "Brak odbiorcy") + " • " + formatDateTime(parcel.getCreatedAt()),
                                    parcel.getStatus().displayName(),
                                    parcelChipVariant(parcel.getStatus())))
                            .toList(),
                    "Brak ostatnich paczek do wyświetlenia.");

            BigDecimal average = parcels.isEmpty()
                    ? BigDecimal.ZERO
                    : total.divide(BigDecimal.valueOf(parcels.size()), 2, java.math.RoundingMode.HALF_UP);
            updateFactTile(averageFact, formatCurrency(average));
            updateFactTile(deliveredFact, String.valueOf(delivered));
            updateFactTile(latestFact, latestParcel == null ? "Brak" : formatDateTime(latestParcel.getCreatedAt()));
            updateFactTile(smallFact, String.valueOf(parcels.stream().filter(parcel -> parcel.getSize() == ParcelSize.SMALL).count()));
            updateFactTile(mediumFact, String.valueOf(parcels.stream().filter(parcel -> parcel.getSize() == ParcelSize.MEDIUM).count()));
            updateFactTile(largeFact, String.valueOf(parcels.stream().filter(parcel -> parcel.getSize() == ParcelSize.LARGE).count()));

            VBox updatedChart = dashboardBarChartCard("Wysłane przesyłki",
                    dayLabels(days),
                    parcelCountsByCreatedDay(parcels, days));
            GridPane parent = (GridPane) chartCard.getParent();
            if (parent != null) {
                parent.getChildren().remove(chartCard);
                placeDashboardCard(parent, updatedChart, 0, 0, 2, 1);
            }
        });
    }

    private void loadCourierDashboard(VBox page) {
        List<LocalDate> days = lastDays(5);
        VBox chartCard = dashboardBarChartCard("Twoje doręczenia",
                dayLabels(days),
                List.of(0L, 0L, 0L, 0L, 0L));

        VBox taskList = dashboardListContainer("Brak zleceń do wyświetlenia.");
        VBox taskCard = dashboardSectionCard("Ostatnie zlecenia",
                taskList);

        VBox vehicleFact = dashboardMiniFact("...", "Pojazd");
        VBox totalFact = dashboardMiniFact("...", "Doręczone łącznie");
        VBox problemFact = dashboardMiniFact("...", "Problemy");
        VBox summaryCard = dashboardFactStackCard("Bilans kuriera",
                vehicleFact, totalFact, problemFact);

        VBox currentStageFact = dashboardMiniFact("...", "Etap");
        VBox currentDestinationFact = dashboardMiniFact("...", "Cel");
        VBox currentWeightFact = dashboardMiniFact("...", "Waga");
        VBox flowCard = dashboardFactStackCard("Aktualne zlecenie",
                currentStageFact, currentDestinationFact, currentWeightFact);

        VBox availabilityFact = dashboardMiniFact("...", "Status");
        VBox syncFact = dashboardMiniFact("...", "Synchronizacja");
        VBox availabilityCard = dashboardFactStackCard("Dostępność",
                availabilityFact, syncFact);

        VBox deliveredTodayFact = dashboardFactTile("Doręczone dziś");
        VBox todayWeightFact = dashboardFactTile("Waga dziś");
        VBox lastDeliveryFact = dashboardFactTile("Ostatnia dostawa");
        VBox statsCard = dashboardSectionCard("Podsumowanie pracy",
                dashboardFactsRow(deliveredTodayFact, todayWeightFact, lastDeliveryFact));

        GridPane layout = dashboardThreeColumnGrid(34, 32, 34);
        placeDashboardCard(layout, chartCard, 0, 0, 2, 1);
        placeDashboardCard(layout, summaryCard, 2, 0);
        placeDashboardCard(layout, flowCard, 0, 1);
        placeDashboardCard(layout, taskCard, 1, 1, 2, 1);
        placeDashboardCard(layout, availabilityCard, 0, 2);
        placeDashboardCard(layout, statsCard, 1, 2, 2, 1);
        page.getChildren().add(layout);

        send(MessageType.LIST_COURIERS_REQUEST, new TokenRequest(session.token()), response -> {
            List<Courier> couriers = session.apiClient().dataList(response, Courier.class);
            Courier currentCourier = couriers.stream()
                    .filter(courier -> courier.getUserId() == session.user().getId())
                    .findFirst()
                    .orElse(null);
            if (currentCourier == null) {
                return;
            }

            updateFactTile(vehicleFact, safeText(currentCourier.getVehicleNumber(), "Brak numeru"));
            updateFactTile(totalFact, "...");
            updateFactTile(availabilityFact, currentCourier.getStatus().displayName());
            updateFactTile(syncFact, formatDateTime(currentCourier.getUpdatedAt()));

            send(MessageType.LIST_PARCELS_REQUEST, new TokenRequest(session.token()), parcelResponse -> {
                List<Parcel> allParcels = session.apiClient().dataList(parcelResponse, Parcel.class);
                List<Parcel> parcels = parcelsForCourier(allParcels, currentCourier.getId());
                long deliveredToday = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.DELIVERED)
                        .filter(p -> p.getUpdatedAt() != null && p.getUpdatedAt().toLocalDate().equals(LocalDate.now()))
                        .count();
                double deliveredWeightToday = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.DELIVERED)
                        .filter(p -> p.getUpdatedAt() != null && p.getUpdatedAt().toLocalDate().equals(LocalDate.now()))
                        .mapToDouble(Parcel::getWeightKg)
                        .sum();
                long totalDelivered = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.DELIVERED).count();
                long problems = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.DELIVERY_PROBLEM).count();
                Parcel activeParcel = parcels.stream()
                        .filter(parcel -> isActiveParcel(parcel.getStatus()))
                        .sorted(Comparator.comparingInt(DashboardView::parcelPriority))
                        .findFirst()
                        .orElse(null);
                Parcel lastDelivered = parcels.stream()
                        .filter(parcel -> parcel.getStatus() == ParcelStatus.DELIVERED)
                        .sorted(Comparator.comparing(Parcel::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                        .findFirst()
                        .orElse(null);

                updateFactTile(totalFact, String.valueOf(totalDelivered));
                updateFactTile(problemFact, String.valueOf(problems));
                updateFactTile(currentStageFact, activeParcel == null ? "Brak" : activeParcel.getStatus().displayName());
                updateFactTile(currentDestinationFact, activeParcel == null ? "Brak" : shorten(activeParcelDestination(activeParcel), 28));
                updateFactTile(currentWeightFact, activeParcel == null ? "Brak" : formatWeight(activeParcel.getWeightKg()));
                updateFactTile(deliveredTodayFact, String.valueOf(deliveredToday));
                updateFactTile(todayWeightFact, formatWeight(deliveredWeightToday));
                updateFactTile(lastDeliveryFact, lastDelivered == null ? "Brak" : formatDateTime(lastDelivered.getUpdatedAt()));

                populateDashboardList(taskList,
                        parcels.stream()
                                .sorted(Comparator.comparing(Parcel::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                                .limit(3)
                                .map(parcel -> dashboardListItem(
                                        safeText(parcel.getTitle(), "Bez tytułu"),
                                        safeText(parcel.getReceiverAddressText(), "Brak adresu") + " • " + formatDateTime(parcel.getUpdatedAt()),
                                        parcel.getStatus().displayName(),
                                        parcelChipVariant(parcel.getStatus())))
                                .toList(),
                        "Brak zleceń do wyświetlenia.");

                VBox updatedChart = dashboardBarChartCard("Twoje doręczenia",
                        dayLabels(days),
                        deliveredParcelCountsByDay(parcels, days));
                GridPane parent = (GridPane) chartCard.getParent();
                if (parent != null) {
                    parent.getChildren().remove(chartCard);
                    placeDashboardCard(parent, updatedChart, 0, 0, 2, 1);
                }
            });
        });
    }

    private void loadDispatcherDashboard(VBox page) {
        List<LocalDate> days = lastDays(5);
        VBox chartCard = dashboardBarChartCard("Doręczone przesyłki",
                dayLabels(days),
                List.of(0L, 0L, 0L, 0L, 0L));

        VBox deliveredTodayFact = dashboardMiniFact("...", "Doręczone dziś");
        VBox createdTodayFact = dashboardMiniFact("...", "Nowe dziś");
        VBox problemFact = dashboardMiniFact("...", "Problemy doręczeń");
        VBox summaryCard = dashboardFactStackCard("Stan dnia",
                deliveredTodayFact, createdTodayFact, problemFact);

        VBox pickupFact = dashboardMiniFactInline("...", "W odbiorze");
        VBox warehouseFact = dashboardMiniFactInline("...", "W centrali");
        VBox transitFact = dashboardMiniFactInline("...", "W drodze");
        VBox parcelFlowCard = dashboardFactStackCard("Paczki w obiegu",
                pickupFact, warehouseFact, transitFact);

        VBox availableFact = dashboardFactTile("Dostępni");
        VBox busyFact = dashboardFactTile("Zajęci");
        VBox breakFact = dashboardFactTile("Przerwa");
        VBox fleetCard = dashboardSectionCard("Dostępność floty",
                dashboardFactsRow(availableFact, busyFact, breakFact));

        VBox eventList = dashboardListContainer("Brak zdarzeń do wyświetlenia.");
        VBox eventCard = dashboardSectionCard("Ostatnie zdarzenia",
                eventList);

        VBox waitingFactCard = dashboardFactTile("Oczekują na kuriera");
        VBox deliveredTotalFact = dashboardFactTile("Doręczone łącznie");
        VBox pressureCard = dashboardSectionCard("Najważniejsze liczby",
                dashboardFactsRow(waitingFactCard, deliveredTotalFact));

        GridPane layout = dashboardThreeColumnGrid(34, 32, 34);
        configureDashboardRows(layout, 34, 34, 32);
        placeDashboardCard(layout, chartCard, 0, 0, 2, 1);
        placeDashboardCard(layout, summaryCard, 2, 0);
        placeDashboardCard(layout, eventCard, 0, 1, 1, 2);
        placeDashboardCard(layout, parcelFlowCard, 1, 1);
        placeDashboardCard(layout, fleetCard, 2, 1);
        placeDashboardCard(layout, pressureCard, 1, 2, 2, 1);
        page.getChildren().add(layout);

        send(MessageType.LIST_PARCELS_REQUEST, new TokenRequest(session.token()), response -> {
            List<Parcel> parcels = session.apiClient().dataList(response, Parcel.class);
            long waiting = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.WAITING_FOR_COURIER).count();
            long pickup = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.PICKUP_IN_PROGRESS).count();
            long warehouse = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.WAREHOUSE).count();
            long transit = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.IN_TRANSIT
                    || p.getStatus() == ParcelStatus.OUT_FOR_DELIVERY).count();
            long delivered = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.DELIVERED).count();
            long deliveredToday = parcels.stream()
                    .filter(p -> p.getStatus() == ParcelStatus.DELIVERED)
                    .filter(p -> p.getUpdatedAt() != null && p.getUpdatedAt().toLocalDate().equals(LocalDate.now()))
                    .count();
            long createdToday = parcels.stream()
                    .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().toLocalDate().equals(LocalDate.now()))
                    .count();
            long problems = parcels.stream().filter(p -> p.getStatus() == ParcelStatus.DELIVERY_PROBLEM).count();
            updateFactTile(deliveredTodayFact, String.valueOf(deliveredToday));
            updateFactTile(createdTodayFact, String.valueOf(createdToday));
            updateFactTile(problemFact, String.valueOf(problems));
            updateFactTile(pickupFact, String.valueOf(pickup));
            updateFactTile(warehouseFact, String.valueOf(warehouse));
            updateFactTile(transitFact, String.valueOf(transit));
            updateFactTile(waitingFactCard, String.valueOf(waiting));
            updateFactTile(deliveredTotalFact, String.valueOf(delivered));

            VBox updatedChart = dashboardBarChartCard("Doręczone przesyłki",
                    dayLabels(days),
                    deliveredParcelCountsByDay(parcels, days));
            GridPane parent = (GridPane) chartCard.getParent();
            if (parent != null) {
                parent.getChildren().remove(chartCard);
                placeDashboardCard(parent, updatedChart, 0, 0, 2, 1);
            }
        });
        send(MessageType.LIST_COURIERS_REQUEST, new TokenRequest(session.token()), response -> {
            List<Courier> couriers = session.apiClient().dataList(response, Courier.class);
            long available = couriers.stream().filter(c -> c.getStatus() == CourierStatus.AVAILABLE).count();
            long busy = couriers.stream().filter(c -> c.getStatus() == CourierStatus.BUSY).count();
            long onBreak = couriers.stream().filter(c -> c.getStatus() == CourierStatus.BREAK).count();
            updateFactTile(availableFact, String.valueOf(available));
            updateFactTile(busyFact, String.valueOf(busy));
            updateFactTile(breakFact, String.valueOf(onBreak));
        });
        send(MessageType.LIST_EVENTS_REQUEST, new TokenRequest(session.token()), response -> {
            List<EventLog> events = session.apiClient().dataList(response, EventLog.class);
            populateDashboardList(eventList,
                    events.stream()
                            .sorted(Comparator.comparing(EventLog::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                            .limit(5)
                            .map(event -> dashboardListItem(
                                    safeText(event.getLevel(), "INFO"),
                                    dashboardEventMessage(event),
                                    formatDateTime(event.getCreatedAt()),
                                    eventLevelVariant(event.getLevel())))
                            .toList(),
                    "Brak zdarzeń do wyświetlenia.");
        });
    }

    private void loadAdminDashboard(VBox page) {
        List<LocalDate> days = lastDays(5);
        VBox chartCard = dashboardBarChartCard("Nowi klienci",
                dayLabels(days),
                List.of(0L, 0L, 0L, 0L, 0L));

        VBox totalFact = dashboardMiniFact("...", "Użytkownicy ogółem");
        VBox activeFact = dashboardMiniFact("...", "Aktywne konta");
        VBox blockedFact = dashboardMiniFact("...", "Zablokowani");
        VBox summaryCard = dashboardFactStackCard("Stan systemu",
                totalFact, activeFact, blockedFact);

        VBox clientsRow = dashboardProgressRow("Klienci");
        VBox couriersRow = dashboardProgressRow("Kurierzy");
        VBox dispatchersRow = dashboardProgressRow("Dyspozytorzy");
        VBox adminsRow = dashboardProgressRow("Administratorzy");
        VBox rolesCard = dashboardSectionCard("Struktura ról",
                clientsRow, couriersRow, dispatchersRow, adminsRow);
        rolesCard.getStyleClass().add("dashboard-roles-card");

        VBox usersList = dashboardListContainer("Brak użytkowników do wyświetlenia.");
        VBox latestUsersCard = dashboardSectionCard("Najnowsze konta",
                usersList);

        VBox simulationCard = dashboardSectionCard("Symulacja",
                simulationControls());

        GridPane layout = dashboardThreeColumnGrid(34, 32, 34);
        placeDashboardCard(layout, chartCard, 0, 0, 2, 1);
        placeDashboardCard(layout, summaryCard, 2, 0);
        placeDashboardCard(layout, latestUsersCard, 0, 1, 1, 2);
        placeDashboardCard(layout, rolesCard, 1, 1, 2, 1);
        placeDashboardCard(layout, simulationCard, 1, 2, 2, 1);
        page.getChildren().add(layout);

        send(MessageType.LIST_USERS_REQUEST, new TokenRequest(session.token()), response -> {
            List<User> users = session.apiClient().dataList(response, User.class);
            long clients = users.stream().filter(u -> u.getRole() == UserRole.CLIENT).count();
            long couriers = users.stream().filter(u -> u.getRole() == UserRole.COURIER).count();
            long dispatchers = users.stream().filter(u -> u.getRole() == UserRole.DISPATCHER).count();
            long blocked = users.stream().filter(User::isBlocked).count();
            long admins = users.stream().filter(u -> u.getRole() == UserRole.ADMIN).count();
            updateFactTile(totalFact, String.valueOf(users.size()));
            updateFactTile(activeFact, String.valueOf(users.size() - blocked));
            updateFactTile(blockedFact, String.valueOf(blocked));

            updateDashboardProgress(clientsRow, clients, users.size());
            updateDashboardProgress(couriersRow, couriers, users.size());
            updateDashboardProgress(dispatchersRow, dispatchers, users.size());
            updateDashboardProgress(adminsRow, admins, users.size());

            populateDashboardList(usersList,
                    users.stream()
                            .sorted(Comparator.comparing(User::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                            .limit(5)
                            .map(user -> dashboardListItem(
                                    safeText(user.getFullName(), "Bez nazwy"),
                                    user.getRole().displayName() + " • " + formatDateTime(user.getUpdatedAt()),
                                    user.isBlocked() ? "Zablokowane" : "Aktywne",
                                    user.isBlocked() ? "danger" : "success"))
                            .toList(),
                    "Brak użytkowników do wyświetlenia.");

            VBox updatedChart = dashboardBarChartCard("Nowi klienci",
                    dayLabels(days),
                    clientRegistrationCountsByDay(users, days));
            GridPane parent = (GridPane) chartCard.getParent();
            if (parent != null) {
                parent.getChildren().remove(chartCard);
                placeDashboardCard(parent, updatedChart, 0, 0, 2, 1);
            }
        });
    }

    private VBox simulationControls() {
        Label statusDot = new Label("•");
        statusDot.getStyleClass().addAll("simulation-status-dot", "simulation-status-unknown");
        Label statusText = new Label("Stan: pobieranie...");
        statusText.getStyleClass().add("simulation-status-text");
        statusText.setWrapText(true);
        HBox statusRow = new HBox(8, statusDot, statusText);
        statusRow.getStyleClass().add("simulation-status-row");
        statusRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusText, Priority.ALWAYS);

        Button startSimulation = new Button("Uruchom symulację");
        Button stopSimulation = new Button("Zatrzymaj symulację");
        startSimulation.getStyleClass().add("compact-button");
        stopSimulation.getStyleClass().add("compact-button");
        startSimulation.setMaxWidth(Double.MAX_VALUE);
        stopSimulation.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(startSimulation, Priority.ALWAYS);
        HBox.setHgrow(stopSimulation, Priority.ALWAYS);
        startSimulation.setOnAction(event -> send(MessageType.START_SIMULATION_REQUEST, new TokenRequest(session.token()),
                response -> {
                    setSimulationStatus(statusDot, statusText, true);
                    UiDialogs.info("Sterowanie symulacją", session.apiClient().responseMessage(response));
                    refreshSimulationStatus(statusDot, statusText);
                }));
        stopSimulation.setOnAction(event -> send(MessageType.STOP_SIMULATION_REQUEST, new TokenRequest(session.token()),
                response -> {
                    setSimulationStatus(statusDot, statusText, false);
                    UiDialogs.info("Sterowanie symulacją", session.apiClient().responseMessage(response));
                    refreshSimulationStatus(statusDot, statusText);
                }));
        HBox row = new HBox(8, startSimulation, stopSimulation);
        row.getStyleClass().add("dashboard-action-row");
        VBox controls = new VBox(9, statusRow, row);
        controls.getStyleClass().add("simulation-controls");
        controls.setFillWidth(true);
        refreshSimulationStatus(statusDot, statusText);
        return controls;
    }

    private void refreshSimulationStatus(Label statusDot, Label statusText) {
        send(MessageType.SIMULATION_STATUS_REQUEST, new TokenRequest(session.token()),
                response -> setSimulationStatus(statusDot, statusText, session.apiClient().data(response, Boolean.class)));
    }

    private void setSimulationStatus(Label statusDot, Label statusText, Boolean running) {
        statusDot.getStyleClass().removeAll("simulation-status-running", "simulation-status-stopped", "simulation-status-unknown");
        if (Boolean.TRUE.equals(running)) {
            statusDot.getStyleClass().add("simulation-status-running");
            statusText.setText("Stan: uruchomiona");
        } else if (Boolean.FALSE.equals(running)) {
            statusDot.getStyleClass().add("simulation-status-stopped");
            statusText.setText("Stan: zatrzymana");
        } else {
            statusDot.getStyleClass().add("simulation-status-unknown");
            statusText.setText("Stan: pobieranie...");
        }
    }

    private void showClientParcels() {
        ParcelTableBundle bundle = parcelTableBundle();
        TableView<Parcel> table = bundle.table();
        Button refresh = new Button("Odśwież");
        Button cancel = new Button("Anuluj paczkę");
        cancel.getStyleClass().add("danger-button");
        Button details = new Button("Szczegóły");
        refresh.setOnAction(event -> loadParcels(bundle));
        cancel.setOnAction(event -> {
            Parcel selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiDialogs.error("Wybierz paczkę.");
                return;
            }
            if (!selected.getStatus().canBeCanceled()) {
                UiDialogs.error("Tej paczki nie można już anulować.");
                return;
            }
            send(MessageType.CANCEL_PARCEL_REQUEST, new IdRequest(session.token(), selected.getId()), response -> {
                UiDialogs.info(session.apiClient().responseMessage(response));
                loadParcels(bundle);
            });
        });
        details.setOnAction(event -> showParcelDetails(table.getSelectionModel().getSelectedItem()));
        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && isDataRowClick(event)) {
                showParcelDetails(table.getSelectionModel().getSelectedItem());
            }
        });
        setContent(wrap("Moje paczki",
                tableTopBar(bundle.filters(), new HBox(8, refresh, details)),
                tableSeparator(),
                table,
                selectedActions(cancel)));
        loadParcels(bundle);
    }

    private void showCreateParcel() {
        loadClientAddresses(addresses -> {
            TextField title = new TextField();
            TextArea description = new TextArea();
            description.setPrefRowCount(3);
            description.setWrapText(true);
            description.setMinHeight(96);
            ComboBox<ParcelSize> size = new ComboBox<>(FXCollections.observableArrayList(ParcelSize.values()));
            size.getSelectionModel().select(ParcelSize.SMALL);
            stabilizeComboBox(size);
            TextField weight = new TextField("1.0");
            Label pricePreviewError = new Label();
            pricePreviewError.getStyleClass().add("price-preview-error");
            pricePreviewError.setAlignment(Pos.CENTER_RIGHT);
            pricePreviewError.setMinWidth(Region.USE_PREF_SIZE);
            pricePreviewError.setMaxWidth(300);
            Label pricePreview = new Label();
            pricePreview.getStyleClass().add("price-preview");
            pricePreview.setAlignment(Pos.CENTER);
            pricePreview.setMaxWidth(Double.MAX_VALUE);
            TextField receiverFirstName = new TextField();
            TextField receiverLastName = new TextField();
            TextField receiverPhone = new TextField();
            AddressFields senderAddress = addressFields();
            AddressFields receiverAddress = addressFields();
            AddressBookOptions senderOptions = addressBookOptions(addresses, "np. Dom, Praca, Akademik", senderAddress);
            AddressBookOptions receiverOptions = addressBookOptions(addresses, "np. Rodzina, Firma, Punkt odbioru", receiverAddress);
            configureAddressAutocomplete(senderAddress);
            configureAddressAutocomplete(receiverAddress);
            Button submit = new Button("Nadaj paczkę");
            submit.getStyleClass().add("primary-action-button");
            VBox parcelForm = new VBox(14,
                    singleField("Tytuł", title),
                    singleField("Opis", description),
                    twoColumnRow("Rozmiar", size, "Waga (kg)", weight)
            );
            PauseTransition pricePreviewDelay = new PauseTransition(javafx.util.Duration.millis(350));
            AtomicLong pricePreviewVersion = new AtomicLong();
            Runnable updatePricePreview = () -> refreshParcelPricePreview(
                    pricePreview, pricePreviewError, size.getValue(), weight.getText(), senderAddress, receiverAddress,
                    pricePreviewDelay, pricePreviewVersion);
            size.valueProperty().addListener((observable, oldValue, newValue) -> updatePricePreview.run());
            weight.textProperty().addListener((observable, oldValue, newValue) -> updatePricePreview.run());
            bindAddressPricePreview(senderAddress, updatePricePreview);
            bindAddressPricePreview(receiverAddress, updatePricePreview);
            updatePricePreview.run();

            VBox receiverForm = new VBox(14,
                    twoColumnRow("Imię", receiverFirstName, "Nazwisko", receiverLastName),
                    singleField("Telefon odbiorcy", receiverPhone)
            );

            submit.setOnAction(event -> {
                if (isBlank(title.getText()) || isBlank(receiverFirstName.getText()) || isBlank(receiverLastName.getText())) {
                    UiDialogs.error("Uzupełnij wszystkie wymagane dane paczki.");
                    return;
                }
                double weightValue;
                try {
                    weightValue = Double.parseDouble(weight.getText().replace(",", "."));
                } catch (NumberFormatException e) {
                    UiDialogs.error("Waga musi być liczbą.");
                    return;
                }
                if (weightValue <= 0) {
                    UiDialogs.error("Waga paczki musi być większa od zera.");
                    return;
                }
                if (!PhoneUtil.isValid(receiverPhone.getText())) {
                    UiDialogs.error("Numer telefonu odbiorcy musi mieć 9 cyfr.");
                    return;
                }
                if (!validateAddressSelection("nadania", senderOptions)
                        || !validateAddressSelection("odbioru", receiverOptions)) {
                    return;
                }
                String senderAddressText;
                String receiverAddressText;
                long senderMapPointId;
                long receiverMapPointId;
                try {
                    senderAddressText = composeAddress(senderAddress, "nadania");
                    receiverAddressText = composeAddress(receiverAddress, "odbioru");
                    senderMapPointId = 0;
                    receiverMapPointId = 0;
                } catch (IllegalArgumentException e) {
                    UiDialogs.error(e.getMessage());
                    return;
                }
                if (sameAddress(senderAddressText, receiverAddressText)) {
                    UiDialogs.error("Adres odbioru nie może być taki sam jak adres nadania.");
                    return;
                }
                String receiverName = receiverFirstName.getText().trim() + " " + receiverLastName.getText().trim();
                CreateParcelRequest request = new CreateParcelRequest(session.token(), title.getText(), description.getText(),
                        size.getValue(), weightValue, receiverName, PhoneUtil.normalize(receiverPhone.getText()),
                        senderAddressText, senderMapPointId, receiverAddressText,
                        receiverMapPointId, senderOptions.saveAddress().isSelected(), senderOptions.addressName().getText(),
                        receiverOptions.saveAddress().isSelected(), receiverOptions.addressName().getText());
                Runnable createParcel = () -> send(MessageType.CREATE_PARCEL_REQUEST, request, response -> {
                    AssignmentResult result = session.apiClient().data(response, AssignmentResult.class);
                    UiDialogs.custom("Operacja zakończona powodzeniem", createParcelSuccessView(result),
                            920, 600, "parcel-success-dialog");
                    showClientParcels();
                });
                confirmDuplicateParcelTitle(title.getText(), createParcel);
            });
            StackPane stickyPrice = new StackPane(pricePreview);
            stickyPrice.getStyleClass().add("sticky-price");
            stickyPrice.setAlignment(Pos.CENTER);
            stickyPrice.setMinWidth(206);
            stickyPrice.setPrefWidth(220);
            stickyPrice.setMaxWidth(Region.USE_PREF_SIZE);
            stickyPrice.minHeightProperty().bind(submit.heightProperty());
            stickyPrice.prefHeightProperty().bind(submit.heightProperty());
            stickyPrice.maxHeightProperty().bind(submit.heightProperty());
            HBox pricePreviewCluster = new HBox(8, pricePreviewError, stickyPrice);
            pricePreviewCluster.setAlignment(Pos.CENTER_RIGHT);
            pricePreviewCluster.setMaxWidth(Region.USE_PREF_SIZE);
            Region actionSpacer = new Region();
            HBox.setHgrow(actionSpacer, Priority.ALWAYS);
            HBox submitRow = new HBox(12, submit, actionSpacer, pricePreviewCluster);
            submitRow.setAlignment(Pos.CENTER_LEFT);
            submitRow.setMaxWidth(Double.MAX_VALUE);
            VBox page = new VBox(14,
                    formSection("Dane paczki", parcelForm),
                    formSection("Dane odbiorcy", receiverForm),
                    addressSection("Adres nadania", senderAddress, senderOptions),
                    addressSection("Adres odbioru", receiverAddress, receiverOptions),
                    submitRow
            );
            page.getStyleClass().add("form-page");
            ScrollPane scrollPane = new ScrollPane(page);
            scrollPane.setFitToWidth(true);
            scrollPane.getStyleClass().add("content-scroll");
            setContent(wrap("Nadanie paczki", scrollPane));
        });
    }

    private void showAddresses() {
            AddressTableBundle bundle = addressTableBundle();
            TableView<ClientAddress> table = bundle.table();
            TextField name = new TextField();
            AddressFields address = addressFields();
            configureAddressAutocomplete(address);
            TextField notes = new TextField();
            Button save = new Button("Zapisz adres");
            Button delete = new Button("Usuń adres");
            delete.getStyleClass().add("danger-button");
            save.setOnAction(event -> {
                ClientAddress selected = table.getSelectionModel().getSelectedItem();
                Long id = selected == null ? null : selected.getId();
                String addressText;
                long mapPointId;
                try {
                    addressText = composeAddress(address, "adresu");
                    mapPointId = 0;
                } catch (IllegalArgumentException e) {
                    UiDialogs.error(e.getMessage());
                    return;
                }
                AddressRequest request = new AddressRequest(session.token(), id, name.getText(), addressText, mapPointId, notes.getText());
                send(MessageType.SAVE_ADDRESS_REQUEST, request, response -> {
                    UiDialogs.info(session.apiClient().responseMessage(response));
                    loadAddresses(bundle);
                });
            });
            delete.setOnAction(event -> {
                ClientAddress selected = table.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    UiDialogs.error("Wybierz adres do usunięcia.");
                    return;
                }
                if (!UiDialogs.confirm("Czy na pewno usunąć zapisany adres \"" + selected.getName() + "\"?")) {
                    return;
                }
                send(MessageType.DELETE_ADDRESS_REQUEST, new IdRequest(session.token(), selected.getId()), response -> {
                    UiDialogs.info(session.apiClient().responseMessage(response));
                    table.getSelectionModel().clearSelection();
                    name.clear();
                    notes.clear();
                    clearAddressFields(address);
                    loadAddresses(bundle);
                });
            });
            table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
                if (selected != null) {
                    name.setText(selected.getName());
                    fillAddressFields(address, selected);
                    notes.setText(nullToEmpty(selected.getNotes()));
                }
            });
            GridPane form = form();
            add(form, 0, "Nazwa", name);
            add(form, 1, "Uwagi", notes);
            VBox addressPage = new VBox(12,
                    tableTopBar(bundle.filters(), new HBox()),
                    tableSeparator(),
                    table,
                    formSection("Dane adresu", form),
                    addressSection("Adres", address),
                    selectedActions(save, delete));
            addressPage.getStyleClass().add("form-page");
            ScrollPane scrollPane = scrollContent(addressPage);
            setContent(wrap("Moje adresy", scrollPane));
            loadAddresses(bundle);
    }

    private void showCourierParcels() {
        ParcelTableBundle bundle = parcelTableBundle();
        TableView<Parcel> table = bundle.table();
        ComboBox<ParcelStatus> status = new ComboBox<>(FXCollections.observableArrayList(courierSelectableParcelStatuses()));
        status.getSelectionModel().select(ParcelStatus.PICKUP_IN_PROGRESS);
        status.setPrefWidth(230);
        stabilizeComboBox(status);
        TextField note = new TextField();
        note.setPromptText("Notatka do historii statusu");
        note.setPrefWidth(520);
        note.setMinWidth(360);
        Button update = new Button("Zmień status");
        update.setOnAction(event -> {
            Parcel selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiDialogs.error("Wybierz paczkę.");
                return;
            }
            if (isTerminalStatus(selected.getStatus())) {
                UiDialogs.error("Ta paczka jest zakończona. Nie można zmienić jej statusu.");
                return;
            }
            send(MessageType.UPDATE_PARCEL_STATUS_REQUEST,
                    new ParcelStatusRequest(session.token(), selected.getId(), status.getValue(), note.getText()),
                    response -> {
                        UiDialogs.info(session.apiClient().responseMessage(response));
                        loadParcels(bundle);
                    });
        });
        Button refresh = new Button("Odśwież");
        Button details = new Button("Szczegóły");
        refresh.setOnAction(event -> loadParcels(bundle));
        details.setOnAction(event -> showParcelDetails(table.getSelectionModel().getSelectedItem()));
        setContent(wrap("Moje zlecenia",
                tableTopBar(bundle.filters(), new HBox(8, refresh, details)),
                tableSeparator(),
                table,
                selectedActions(status, note, update)));
        loadParcels(bundle);
    }

    private void showDispatcherParcels() {
        ParcelTableBundle bundle = parcelTableBundle();
        TableView<Parcel> parcels = bundle.table();
        ComboBox<Courier> couriers = new ComboBox<>();
        stabilizeComboBox(couriers);
        couriers.setPromptText("Brak dostępnego kuriera");
        couriers.setMinWidth(260);
        couriers.setPrefWidth(300);
        Button assign = new Button("Przypisz kuriera");
        Button refresh = new Button("Odśwież");
        Button details = new Button("Szczegóły");
        refresh.setOnAction(event -> {
            loadParcels(bundle);
            loadCouriers(null, couriers);
        });
        details.setOnAction(event -> showParcelDetails(parcels.getSelectionModel().getSelectedItem()));
        parcels.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && isDataRowClick(event)) {
                showParcelDetails(parcels.getSelectionModel().getSelectedItem());
            }
        });
        assign.setOnAction(event -> {
            Parcel parcel = parcels.getSelectionModel().getSelectedItem();
            Courier courier = couriers.getValue();
            if (parcel == null || courier == null) {
                UiDialogs.error("Wybierz paczkę i kuriera.");
                return;
            }
            if (!canAssignCourierFromPanel(parcel)) {
                UiDialogs.error("Nie można przypisać kuriera. Paczka musi oczekiwać na kuriera albo być w centrali.");
                return;
            }
            send(MessageType.DISPATCH_PARCEL_REQUEST, new AssignCourierRequest(session.token(), parcel.getId(), courier.getId()), response -> {
                UiDialogs.info(session.apiClient().responseMessage(response));
                loadParcels(bundle);
            });
        });
        setContent(wrap("Paczki i przypisania",
                tableTopBar(bundle.filters(), new HBox(8, refresh, details)),
                tableSeparator(),
                parcels,
                selectedActions(couriers, assign)));
        loadParcels(bundle);
        loadCouriers(null, couriers);
    }

    private void showCourierList() {
        CourierTableBundle bundle = courierTableBundle();
        TableView<Courier> table = bundle.table();
        ComboBox<CourierStatus> status = new ComboBox<>(FXCollections.observableArrayList(CourierStatus.values()));
        status.getSelectionModel().select(CourierStatus.AVAILABLE);
        status.setPrefWidth(230);
        stabilizeComboBox(status);
        Button save = new Button("Zmień status");
        Button refresh = new Button("Odśwież");
        save.getStyleClass().add("compact-button");
        refresh.getStyleClass().add("compact-button");
        refresh.setOnAction(event -> loadCouriers(bundle));
        save.setOnAction(event -> {
            Courier selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiDialogs.error("Wybierz kuriera.");
                return;
            }
            send(MessageType.UPDATE_COURIER_STATUS_REQUEST,
                    new CourierStatusRequest(session.token(), selected.getId(), status.getValue()),
                    response -> {
                        UiDialogs.info(session.apiClient().responseMessage(response));
                        loadCouriers(bundle);
                    });
        });
        setContent(wrap("Kurierzy",
                tableTopBar(bundle.filters(), new HBox(8, refresh)),
                tableSeparator(),
                table,
                selectedActions(status, save)));
        loadCouriers(bundle);
    }

    private void showMap() {
        setContent(new OnlineMapView(session));
    }

    private void showEvents() {
        ListView<String> list = new ListView<>();
        Button refresh = new Button("Odśwież");
        Button generate = new Button("Wygeneruj raport");
        refresh.setOnAction(event -> loadEvents(list));
        generate.setOnAction(event -> send(MessageType.GENERATE_REPORT_REQUEST, new TokenRequest(session.token()),
                response -> UiDialogs.info(session.apiClient().responseMessage(response))));
        setContent(wrap("Raport zdarzeń", splitToolbar(new HBox(8, generate), new HBox(8, refresh)), list));
        loadEvents(list);
    }

    private void showAdminUsers() {
        UserTableBundle bundle = userTableBundle();
        TableView<User> table = bundle.table();
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        TextField firstName = new TextField();
        TextField lastName = new TextField();
        TextField email = new TextField();
        TextField phone = new TextField();
        ComboBox<UserRole> role = new ComboBox<>(FXCollections.observableArrayList(UserRole.values()));
        role.getSelectionModel().select(UserRole.CLIENT);
        stabilizeComboBox(role);
        CheckBox blocked = new CheckBox("Konto zablokowane");
        TextField vehicle = new TextField("KR NEW");
        TextField shiftStart = new TextField("06:00");
        TextField shiftEnd = new TextField("14:00");
        CheckBox courierTestMode = new CheckBox("Kurier testowy / poza grafikiem");
        ObservableList<Courier> courierProfiles = FXCollections.observableArrayList();
        VBox roleBox = singleField("Rola", role);
        VBox vehicleRow = singleField("Pojazd kuriera", vehicle);
        VBox shiftStartRow = singleField("Start zmiany", shiftStart);
        VBox shiftEndRow = singleField("Koniec zmiany", shiftEnd);
        HBox.setHgrow(roleBox, Priority.ALWAYS);
        HBox.setHgrow(vehicleRow, Priority.ALWAYS);
        HBox.setHgrow(shiftStartRow, Priority.ALWAYS);
        HBox.setHgrow(shiftEndRow, Priority.ALWAYS);
        roleBox.setMinWidth(0);
        roleBox.setPrefWidth(0);
        roleBox.setMaxWidth(Double.MAX_VALUE);
        vehicleRow.setMinWidth(0);
        vehicleRow.setPrefWidth(0);
        vehicleRow.setMaxWidth(Double.MAX_VALUE);
        shiftStartRow.setMinWidth(0);
        shiftStartRow.setPrefWidth(0);
        shiftStartRow.setMaxWidth(Double.MAX_VALUE);
        shiftEndRow.setMinWidth(0);
        shiftEndRow.setPrefWidth(0);
        shiftEndRow.setMaxWidth(Double.MAX_VALUE);
        HBox roleVehicleRow = new HBox(12, roleBox, vehicleRow);
        roleVehicleRow.setMaxWidth(Double.MAX_VALUE);
        HBox shiftRow = new HBox(12, shiftStartRow, shiftEndRow);
        shiftRow.setMaxWidth(Double.MAX_VALUE);
        Button addUser = new Button("Dodaj nowego użytkownika");
        Button save = new Button("Zapisz użytkownika");
        HBox editButtons = new HBox(8, save);
        editButtons.setAlignment(Pos.CENTER_LEFT);
        HBox flagsRow = new HBox(16, courierTestMode, blocked);
        flagsRow.setAlignment(Pos.CENTER_LEFT);
        Runnable refreshCourierFields = () -> {
            boolean courier = role.getValue() == UserRole.COURIER;
            vehicleRow.setVisible(courier);
            vehicleRow.setManaged(courier);
            vehicleRow.setDisable(!courier);
            shiftRow.setVisible(courier);
            shiftRow.setManaged(courier);
            courierTestMode.setVisible(courier);
            courierTestMode.setManaged(courier);
        };
        role.valueProperty().addListener((observable, oldValue, selected) -> refreshCourierFields.run());
        VBox form = new VBox(10,
                twoColumnRow("Imię", firstName, "Nazwisko", lastName),
                twoColumnRow("Adres e-mail", email, "Nr telefonu", phone),
                roleVehicleRow,
                shiftRow,
                flagsRow,
                editButtons
        );
        form.getStyleClass().add("admin-user-form");
        VBox editSection = formSection("Edycja wybranego użytkownika", form);
        editSection.setVisible(false);
        editSection.setManaged(false);
        addUser.setOnAction(event -> showNewUserDialog(bundle));
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                firstName.setText(selected.getFirstName());
                lastName.setText(selected.getLastName());
                email.setText(selected.getEmail());
                phone.setText(selected.getPhone());
                role.setValue(selected.getRole());
                vehicle.setText(vehicleForUser(selected, courierProfiles));
                shiftStart.setText(shiftStartForUser(selected, courierProfiles));
                shiftEnd.setText(shiftEndForUser(selected, courierProfiles));
                courierTestMode.setSelected(courierTestModeForUser(selected, courierProfiles));
                blocked.setSelected(selected.isBlocked());
                refreshCourierFields.run();
                editSection.setVisible(true);
                editSection.setManaged(true);
            } else {
                editSection.setVisible(false);
                editSection.setManaged(false);
            }
        });
        save.setOnAction(event -> {
            User selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiDialogs.error("Wybierz użytkownika z tabeli.");
                return;
            }
            if (!PhoneUtil.isValid(phone.getText())) {
                UiDialogs.error("Numer telefonu musi mieć 9 cyfr.");
                return;
            }
            SaveUserRequest request = new SaveUserRequest(session.token(), selected.getId(),
                    firstName.getText(), lastName.getText(), email.getText(), PhoneUtil.normalize(phone.getText()),
                    "",
                    role.getValue(), Theme.LIGHT, blocked.isSelected(), vehicle.getText(), null,
                    shiftStart.getText(), shiftEnd.getText(), courierTestMode.isSelected());
            send(MessageType.SAVE_USER_REQUEST, request, response -> {
                String message = session.apiClient().responseMessage(response);
                UiDialogs.info(message);
                loadUsers(bundle);
                loadCourierProfiles(courierProfiles, () -> {
                    User current = table.getSelectionModel().getSelectedItem();
                    if (current != null) {
                        vehicle.setText(vehicleForUser(current, courierProfiles));
                        shiftStart.setText(shiftStartForUser(current, courierProfiles));
                        shiftEnd.setText(shiftEndForUser(current, courierProfiles));
                        courierTestMode.setSelected(courierTestModeForUser(current, courierProfiles));
                    }
                });
            });
        });
        VBox page = wrap("Zarządzanie użytkownikami",
                tableTopBar(bundle.filters(), new HBox(8, addUser)),
                tableSeparator(),
                table,
                editSection);
        VBox.setVgrow(table, Priority.NEVER);
        setContent(page);
        loadUsers(bundle);
        loadCourierProfiles(courierProfiles, () -> {
            User selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                vehicle.setText(vehicleForUser(selected, courierProfiles));
            }
        });
    }

    private void showNewUserDialog(UserTableBundle bundle) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Dodaj nowego użytkownika");
        ButtonType createType = new ButtonType("Dodaj użytkownika", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Anuluj", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, cancelType);

        TextField firstName = new TextField();
        TextField lastName = new TextField();
        TextField email = new TextField();
        TextField phone = new TextField();
        PasswordField password = new PasswordField();
        BooleanProperty showPassword = new SimpleBooleanProperty(false);
        PasswordFieldView.PasswordControl passwordControl = PasswordFieldView.wrap(password, showPassword);
        ComboBox<UserRole> role = new ComboBox<>(FXCollections.observableArrayList(UserRole.values()));
        role.getSelectionModel().select(UserRole.CLIENT);
        stabilizeComboBox(role);
        CheckBox blocked = new CheckBox("Konto zablokowane");
        TextField vehicle = new TextField("KR NEW");
        TextField shiftStart = new TextField("06:00");
        TextField shiftEnd = new TextField("14:00");
        CheckBox courierTestMode = new CheckBox("Kurier testowy / poza grafikiem");
        VBox vehicleRow = singleField("Pojazd kuriera", vehicle);
        HBox shiftRow = twoColumnRow("Start zmiany", shiftStart, "Koniec zmiany", shiftEnd);
        Runnable refreshVehicle = () -> {
            boolean courier = role.getValue() == UserRole.COURIER;
            vehicleRow.setVisible(courier);
            vehicleRow.setManaged(courier);
            shiftRow.setVisible(courier);
            shiftRow.setManaged(courier);
            courierTestMode.setVisible(courier);
            courierTestMode.setManaged(courier);
        };
        role.valueProperty().addListener((observable, oldValue, selected) -> refreshVehicle.run());
        refreshVehicle.run();

        VBox form = new VBox(14,
                twoColumnRow("Imię", firstName, "Nazwisko", lastName),
                twoColumnRow("Adres e-mail", email, "Nr telefonu", phone),
                singleField("Hasło", passwordControl.node()),
                PasswordFieldView.toggle(showPassword),
                singleField("Rola", role),
                vehicleRow,
                shiftRow,
                courierTestMode,
                blocked
        );
        form.setPadding(new Insets(6));
        form.setPrefWidth(600);
        ScrollPane formScroll = scrollContent(form);
        formScroll.setPrefViewportHeight(430);
        dialog.getDialogPane().setPrefSize(700, 580);
        dialog.getDialogPane().setContent(formScroll);
        styleDialog(dialog);

        Button create = (Button) dialog.getDialogPane().lookupButton(createType);
        create.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            if (isBlank(firstName.getText()) || isBlank(lastName.getText()) || isBlank(email.getText())
                    || isBlank(phone.getText()) || isBlank(password.getText())) {
                UiDialogs.error("Uzupełnij wszystkie dane nowego użytkownika.");
                return;
            }
            if (!PhoneUtil.isValid(phone.getText())) {
                UiDialogs.error("Numer telefonu musi mieć 9 cyfr.");
                return;
            }
            SaveUserRequest request = new SaveUserRequest(session.token(), null,
                    firstName.getText(), lastName.getText(), email.getText(), PhoneUtil.normalize(phone.getText()),
                    password.getText(), role.getValue(), Theme.LIGHT, blocked.isSelected(), vehicle.getText(), null,
                    shiftStart.getText(), shiftEnd.getText(), courierTestMode.isSelected());
            send(MessageType.SAVE_USER_REQUEST, request, response -> {
                UiDialogs.info(session.apiClient().responseMessage(response));
                loadUsers(bundle);
                dialog.close();
            });
        });
        dialog.showAndWait();
    }

    private void showReports() {
        ListView<String> reports = new ListView<>();
        Button refresh = new Button("Odśwież listę");
        Button generate = new Button("Wygeneruj raport");
        Button open = new Button("Otwórz raport");
        refresh.setOnAction(event -> loadReports(reports));
        generate.setOnAction(event -> send(MessageType.GENERATE_REPORT_REQUEST, new TokenRequest(session.token()), response -> {
            UiDialogs.info(session.apiClient().responseMessage(response));
            loadReports(reports);
        }));
        open.setOnAction(event -> openSelectedReport(reports));
        reports.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedReport(reports);
            }
        });
        setContent(wrap("Raporty",
                splitToolbar(new HBox(8, open, generate), new HBox(8, refresh)),
                reports));
        loadReports(reports);
    }

    private void showSettings() {
        TextField firstName = new TextField(session.user().getFirstName());
        TextField lastName = new TextField(session.user().getLastName());
        TextField email = new TextField(session.user().getEmail());
        TextField phone = new TextField(session.user().getPhone());
        ComboBox<Theme> theme = new ComboBox<>(FXCollections.observableArrayList(Theme.values()));
        theme.setValue(session.user().getTheme());
        stabilizeComboBox(theme);
        PasswordField oldPassword = new PasswordField();
        PasswordField newPassword = new PasswordField();
        PasswordField repeatNewPassword = new PasswordField();
        BooleanProperty showPassword = new SimpleBooleanProperty(false);
        PasswordFieldView.PasswordControl oldPasswordControl = PasswordFieldView.wrap(oldPassword, showPassword);
        PasswordFieldView.PasswordControl newPasswordControl = PasswordFieldView.wrap(newPassword, showPassword);
        PasswordFieldView.PasswordControl repeatNewPasswordControl = PasswordFieldView.wrap(repeatNewPassword, showPassword);
        setWide(firstName, lastName, email, phone);

        Button saveTheme = new Button("Zapisz wygląd");
        saveTheme.setOnAction(event -> {
            AccountUpdateRequest request = new AccountUpdateRequest(session.token(), session.user().getFirstName(),
                    session.user().getLastName(), session.user().getEmail(),
                    session.user().getPhone(), theme.getValue(), "", "", "");
            send(MessageType.ACCOUNT_UPDATE_REQUEST, request, response -> {
                User updated = session.apiClient().data(response, User.class);
                session.updateUser(updated);
                onThemeChange.accept(updated.getTheme());
                rightSideBox.getChildren().set(0, buildHeader());
                UiDialogs.info("Wygląd aplikacji został zapisany.");
            });
        });

        Button saveAccount = new Button("Zapisz dane");
        saveAccount.setOnAction(event -> {
            if (!PhoneUtil.isValid(phone.getText())) {
                UiDialogs.error("Numer telefonu musi mieć 9 cyfr.");
                return;
            }
            AccountUpdateRequest request = new AccountUpdateRequest(session.token(), firstName.getText(), lastName.getText(),
                    email.getText(), PhoneUtil.normalize(phone.getText()),
                    session.user().getTheme(), "", "", "");
            send(MessageType.ACCOUNT_UPDATE_REQUEST, request, response -> {
                User updated = session.apiClient().data(response, User.class);
                session.updateUser(updated);
                rightSideBox.getChildren().set(0, buildHeader());
                UiDialogs.info("Dane konta zostały zapisane.");
            });
        });

        Button changePassword = new Button("Zmień hasło");
        changePassword.setOnAction(event -> {
            if (!UiDialogs.confirm("Czy na pewno zmienić hasło do konta?")) {
                return;
            }
            AccountUpdateRequest request = new AccountUpdateRequest(session.token(), session.user().getFirstName(),
                    session.user().getLastName(), session.user().getEmail(),
                    session.user().getPhone(), session.user().getTheme(),
                    oldPassword.getText(), newPassword.getText(), repeatNewPassword.getText());
            send(MessageType.ACCOUNT_UPDATE_REQUEST, request, response -> {
                User updated = session.apiClient().data(response, User.class);
                session.updateUser(updated);
                oldPassword.clear();
                newPassword.clear();
                repeatNewPassword.clear();
                UiDialogs.info(session.apiClient().responseMessage(response));
            });
        });

        GridPane accountForm = form();
        add(accountForm, 0, "Imię", firstName);
        add(accountForm, 1, "Nazwisko", lastName);
        add(accountForm, 2, "Adres e-mail", email);
        add(accountForm, 3, "Telefon", phone);

        GridPane themeForm = form();
        add(themeForm, 0, "Motyw aplikacji", theme);

        GridPane passwordForm = form();
        add(passwordForm, 0, "Stare hasło", oldPasswordControl.node());
        add(passwordForm, 1, "Nowe hasło", newPasswordControl.node());
        add(passwordForm, 2, "Powtórz nowe hasło", repeatNewPasswordControl.node());
        add(passwordForm, 3, "", PasswordFieldView.toggle(showPassword));

        VBox page = new VBox(20,
                settingsSection("Wygląd aplikacji", themeForm, saveTheme),
                settingsSection("Dane konta", accountForm, saveAccount),
                settingsSection("Zmiana hasła", passwordForm, changePassword)
        );
        page.setPadding(new Insets(6, 4, 24, 4));
        page.setMaxWidth(Double.MAX_VALUE);
        ScrollPane scrollPane = new ScrollPane(page);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("settings-scroll");
        setContent(wrap("Ustawienia konta", scrollPane));
    }

    private AddressFields addressFields() {
        TextField street = new TextField();
        TextField city = new TextField("Kraków");
        city.setDisable(true);
        TextField postalCode = new TextField();
        TextField buildingNumber = new TextField();
        TextField apartmentNumber = new TextField();
        return new AddressFields(street, city, postalCode, buildingNumber, apartmentNumber);
    }

    private void configureAddressAutocomplete(AddressFields fields) {
        ContextMenu suggestionsPopup = new ContextMenu();
        suggestionsPopup.getStyleClass().add("address-suggestions-menu");
        suggestionsPopup.setAutoHide(true);
        PauseTransition delay = new PauseTransition(javafx.util.Duration.millis(450));
        AtomicLong requestVersion = new AtomicLong();

        Runnable triggerLookup = () -> {
            if (fields.street().isDisabled()) {
                suggestionsPopup.hide();
                return;
            }
            String query = buildAddressSuggestionQuery(fields);
            if (query == null) {
                delay.stop();
                suggestionsPopup.hide();
                return;
            }
            long requestId = requestVersion.incrementAndGet();
            delay.stop();
            delay.setOnFinished(event -> CompletableFuture.supplyAsync(() -> geocodingService.suggest(query, 5))
                    .whenComplete((suggestions, error) -> Platform.runLater(() -> {
                        if (requestVersion.get() != requestId || fields.street().isDisabled()) {
                            return;
                        }
                        if (error != null || suggestions == null || suggestions.isEmpty()) {
                            suggestionsPopup.hide();
                            return;
                        }
                        showAddressSuggestions(fields, suggestionsPopup, suggestions);
                    })));
            delay.playFromStart();
        };

        fields.street().textProperty().addListener((observable, oldValue, newValue) -> triggerLookup.run());
        fields.buildingNumber().textProperty().addListener((observable, oldValue, newValue) -> triggerLookup.run());
        fields.postalCode().textProperty().addListener((observable, oldValue, newValue) -> triggerLookup.run());
        fields.street().focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused) {
                delay.stop();
            }
        });
    }

    private void showAddressSuggestions(AddressFields fields, ContextMenu popup, List<AddressSuggestion> suggestions) {
        List<CustomMenuItem> items = suggestions.stream()
                .limit(5)
                .map(suggestion -> {
                    Label label = new Label(suggestion.displayName());
                    label.getStyleClass().add("address-suggestion-label");
                    label.setWrapText(true);
                    label.setMaxWidth(360);
                    CustomMenuItem item = new CustomMenuItem(label, true);
                    item.getStyleClass().add("address-suggestion-item");
                    item.setOnAction(event -> {
                        applySuggestedAddress(fields, suggestion);
                        popup.hide();
                    });
                    return item;
                })
                .toList();
        popup.getItems().setAll(items);
        if (!popup.isShowing()) {
            popup.show(fields.street(), javafx.geometry.Side.BOTTOM, 0, 4);
        }
    }

    private void applySuggestedAddress(AddressFields fields, AddressSuggestion suggestion) {
        if (suggestion.street() != null && !suggestion.street().isBlank()) {
            fields.street().setText(suggestion.street());
        }
        if (suggestion.houseNumber() != null && !suggestion.houseNumber().isBlank()) {
            String[] numbers = suggestion.houseNumber().split("/", 2);
            fields.buildingNumber().setText(numbers[0].trim());
            if (numbers.length > 1 && fields.apartmentNumber().getText().isBlank()) {
                fields.apartmentNumber().setText(numbers[1].trim());
            }
        }
        if (suggestion.postalCode() != null && !suggestion.postalCode().isBlank()) {
            fields.postalCode().setText(suggestion.postalCode().trim());
        }
        fields.city().setText(suggestion.normalizedCity());
    }

    private String buildAddressSuggestionQuery(AddressFields fields) {
        String street = nullToEmpty(fields.street().getText()).trim();
        if (street.length() < 3) {
            return null;
        }
        String building = nullToEmpty(fields.buildingNumber().getText()).trim();
        String postalCode = nullToEmpty(fields.postalCode().getText()).trim();
        StringBuilder query = new StringBuilder();
        if (street.toLowerCase(Locale.ROOT).startsWith("ul.")
                || street.toLowerCase(Locale.ROOT).startsWith("al.")
                || street.toLowerCase(Locale.ROOT).startsWith("aleja")
                || street.toLowerCase(Locale.ROOT).startsWith("os.")) {
            query.append(street);
        } else {
            query.append("ul. ").append(street);
        }
        if (!building.isBlank()) {
            query.append(' ').append(building);
        }
        if (!postalCode.isBlank()) {
            query.append(", ").append(postalCode);
        }
        query.append(", Kraków");
        return query.toString();
    }

    private AddressBookOptions addressBookOptions(List<ClientAddress> addresses, String namePrompt, AddressFields fields) {
        CheckBox useSaved = new CheckBox("Wybierz adres z książki adresowej");
        ComboBox<ClientAddress> savedAddress = savedAddressCombo(addresses);
        CheckBox saveAddress = new CheckBox("Zapisz ten adres w książce adresowej");
        TextField addressName = new TextField();
        addressName.setPromptText(namePrompt);
        savedAddress.setDisable(true);
        addressName.setDisable(true);

        savedAddress.valueProperty().addListener((observable, oldValue, selected) -> {
            if (useSaved.isSelected()) {
                fillAddressFields(fields, selected);
            }
        });
        useSaved.selectedProperty().addListener((observable, oldValue, selected) -> {
            if (selected) {
                saveAddress.setSelected(false);
            }
            refreshAddressOptions(fields, useSaved, savedAddress, saveAddress, addressName);
        });
        saveAddress.selectedProperty().addListener((observable, oldValue, selected) ->
                refreshAddressOptions(fields, useSaved, savedAddress, saveAddress, addressName));
        refreshAddressOptions(fields, useSaved, savedAddress, saveAddress, addressName);
        return new AddressBookOptions(useSaved, savedAddress, saveAddress, addressName);
    }

    private void refreshAddressOptions(AddressFields fields, CheckBox useSaved, ComboBox<ClientAddress> savedAddress,
                                       CheckBox saveAddress, TextField addressName) {
        boolean usingSaved = useSaved.isSelected();
        savedAddress.setDisable(!usingSaved || savedAddress.getItems().isEmpty());
        setAddressFieldsDisabled(fields, usingSaved);
        saveAddress.setDisable(usingSaved);
        addressName.setDisable(usingSaved || !saveAddress.isSelected());
    }

    private boolean validateAddressSelection(String kind, AddressBookOptions options) {
        if (options.useSaved().isSelected() && options.savedAddress().getValue() == null) {
            UiDialogs.error("Wybierz zapisany adres " + kind + " albo odznacz wybór z książki adresowej.");
            return false;
        }
        if (options.saveAddress().isSelected() && isBlank(options.addressName().getText())) {
            UiDialogs.error("Podaj nazwę adresu " + kind + " albo odznacz zapis w książce adresowej.");
            return false;
        }
        return true;
    }

    private void setAddressFieldsDisabled(AddressFields fields, boolean disabled) {
        fields.street().setDisable(disabled);
        fields.city().setDisable(true);
        fields.postalCode().setDisable(disabled);
        fields.buildingNumber().setDisable(disabled);
        fields.apartmentNumber().setDisable(disabled);
    }

    private VBox addressSection(String title, AddressFields fields, AddressBookOptions options) {
        VBox savedAddressBox = singleField("Zapisany adres", options.savedAddress());
        VBox addressNameBox = singleField("Nazwa adresu", options.addressName());
        bindVisibility(savedAddressBox, options.useSaved().selectedProperty());
        bindVisibility(options.saveAddress(), options.useSaved().selectedProperty().not());
        bindVisibility(addressNameBox, options.useSaved().selectedProperty().not().and(options.saveAddress().selectedProperty()));
        VBox content = new VBox(12,
                options.useSaved(),
                savedAddressBox,
                addressGrid(fields),
                options.saveAddress(),
                addressNameBox
        );
        content.getStyleClass().add("address-options");
        return formSection(title, content);
    }

    private VBox addressSection(String title, AddressFields fields) {
        return formSection(title, addressGrid(fields));
    }

    private GridPane addressGrid(AddressFields fields) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints first = new ColumnConstraints();
        ColumnConstraints second = new ColumnConstraints();
        ColumnConstraints third = new ColumnConstraints();
        first.setPercentWidth(46);
        second.setPercentWidth(27);
        third.setPercentWidth(27);
        grid.getColumnConstraints().setAll(first, second, third);
        grid.add(addressField("Ulica", fields.street()), 0, 0);
        grid.add(addressField("Numer budynku", fields.buildingNumber()), 1, 0);
        grid.add(addressField("Numer mieszkania", fields.apartmentNumber()), 2, 0);
        grid.add(addressField("Kod pocztowy", fields.postalCode()), 0, 1);
        grid.add(addressField("Miejscowość", fields.city()), 1, 1, 2, 1);
        return grid;
    }

    private VBox addressField(String label, TextField field) {
        Label text = new Label(label);
        field.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(6, text, field);
        box.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private ComboBox<ClientAddress> savedAddressCombo(List<ClientAddress> addresses) {
        ComboBox<ClientAddress> comboBox = new ComboBox<>(FXCollections.observableArrayList(addresses));
        comboBox.setPromptText(addresses.isEmpty() ? "Brak zapisanych adresów" : "Wybierz zapisany adres");
        comboBox.setDisable(addresses.isEmpty());
        stabilizeComboBox(comboBox);
        return comboBox;
    }

    private void fillAddressFields(AddressFields fields, ClientAddress address) {
        if (address == null) {
            return;
        }
        String value = address.getAddressText() == null ? "" : address.getAddressText().trim();
        String[] cityParts = value.split(",", 2);
        String streetPart = cityParts.length > 0 ? cityParts[0].trim() : value;
        String cityPart = cityParts.length > 1 ? cityParts[1].trim() : "";
        fields.street().setText(streetPart.replaceFirst("(?i)^ul\\.\\s*", "").replaceFirst("\\s+\\d+[A-Za-z]?(?:/\\d+[A-Za-z]?)?$", "").trim());
        String number = extractBuildingNumber(streetPart);
        fields.buildingNumber().setText(number.contains("/") ? number.substring(0, number.indexOf('/')) : number);
        fields.apartmentNumber().setText(number.contains("/") ? number.substring(number.indexOf('/') + 1) : "");
        String postalCode = extractPostalCode(cityPart);
        fields.postalCode().setText(postalCode);
        fields.city().setText(cityPart.replace(postalCode, "").trim().isBlank() ? "Kraków" : cityPart.replace(postalCode, "").trim());
    }

    private void clearAddressFields(AddressFields fields) {
        fields.street().clear();
        fields.buildingNumber().clear();
        fields.apartmentNumber().clear();
        fields.postalCode().clear();
        fields.city().setText("Kraków");
    }

    private String extractBuildingNumber(String streetPart) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+[A-Za-z]?(?:/\\d+[A-Za-z]?)?)\\s*$")
                .matcher(streetPart == null ? "" : streetPart);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractPostalCode(String cityPart) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\b\\d{2}-\\d{3}\\b")
                .matcher(cityPart == null ? "" : cityPart);
        return matcher.find() ? matcher.group() : "";
    }

    private VBox formSection(String title, javafx.scene.Node content) {
        Label label = new Label(title);
        label.getStyleClass().add("subsection-title");
        VBox section = new VBox(10, label, content);
        section.getStyleClass().add("form-section");
        section.setMaxWidth(Double.MAX_VALUE);
        return section;
    }

    private String composeAddress(AddressFields fields, String kind) {
        String street = required(fields.street(), "Uzupełnij ulicę adresu " + kind + ".");
        String city = required(fields.city(), "Uzupełnij miejscowość adresu " + kind + ".");
        String postalCode = required(fields.postalCode(), "Uzupełnij kod pocztowy adresu " + kind + ".");
        if (!postalCode.matches("\\d{2}-\\d{3}")) {
            throw new IllegalArgumentException("Kod pocztowy adresu " + kind + " musi mieć format 00-000.");
        }
        if (!"Kraków".equalsIgnoreCase(city.trim())) {
            throw new IllegalArgumentException("Aplikacja obsługuje tylko adresy z miejscowością Kraków.");
        }
        String buildingNumber = required(fields.buildingNumber(), "Uzupełnij numer budynku adresu " + kind + ".");
        String apartmentNumber = fields.apartmentNumber().getText() == null ? "" : fields.apartmentNumber().getText().trim();
        String streetPart = street.toLowerCase(Locale.ROOT).startsWith("ul.")
                || street.toLowerCase(Locale.ROOT).startsWith("al.")
                || street.toLowerCase(Locale.ROOT).startsWith("aleja")
                || street.toLowerCase(Locale.ROOT).startsWith("os.")
                ? street
                : "ul. " + street;
        String number = apartmentNumber.isBlank() ? buildingNumber : buildingNumber + "/" + apartmentNumber;
        return streetPart + " " + number + ", " + postalCode + " " + city;
    }

    private void bindAddressPricePreview(AddressFields fields, Runnable updatePricePreview) {
        fields.street().textProperty().addListener((observable, oldValue, newValue) -> updatePricePreview.run());
        fields.buildingNumber().textProperty().addListener((observable, oldValue, newValue) -> updatePricePreview.run());
        fields.apartmentNumber().textProperty().addListener((observable, oldValue, newValue) -> updatePricePreview.run());
        fields.postalCode().textProperty().addListener((observable, oldValue, newValue) -> updatePricePreview.run());
    }

    private void refreshParcelPricePreview(Label label, Label errorLabel, ParcelSize size, String weightText,
                                           AddressFields senderAddress, AddressFields receiverAddress,
                                           PauseTransition delay, AtomicLong version) {
        if (size == null) {
            setPricePreviewMessage(label, errorLabel, "wybierz rozmiar");
            return;
        }
        Double weightValue = parsePositiveWeight(weightText);
        if (weightValue == null) {
            setPricePreviewMessage(label, errorLabel, "popraw wagę");
            return;
        }
        setPricePreviewMessage(label, errorLabel, "uzupełnij komplet adresów");
        String sender = composeAddressSilently(senderAddress);
        String receiver = composeAddressSilently(receiverAddress);
        if (sender == null || receiver == null) {
            setPricePreviewMessage(label, errorLabel, "uzupełnij komplet adresów");
            return;
        }
        String routeKey = priceRouteKey(sender, receiver);
        Double cachedRouteMinutes = priceRouteMinutesCache.get(routeKey);
        if (cachedRouteMinutes != null) {
            setPricePreviewPrice(label, errorLabel, PricingFormula.calculate(size, weightValue, cachedRouteMinutes));
            return;
        }

        long requestId = version.incrementAndGet();
        delay.stop();
        delay.setOnFinished(event -> {
            setPricePreviewMessage(label, errorLabel, "przeliczanie...");
            CompletableFuture.supplyAsync(() -> calculatePricePreview(size, weightValue, sender, receiver))
                    .whenComplete((result, error) -> Platform.runLater(() -> {
                        if (version.get() != requestId) {
                            return;
                        }
                        if (error != null) {
                            setPricePreviewMessage(label, errorLabel, "sprawdź adresy");
                            return;
                        }
                        setPricePreviewPrice(label, errorLabel, result);
                    }));
        });
        delay.playFromStart();
    }

    private BigDecimal calculatePricePreview(ParcelSize size, double weightValue, String senderAddress, String receiverAddress) {
        String routeKey = priceRouteKey(senderAddress, receiverAddress);
        double routeMinutes = priceRouteMinutesCache.computeIfAbsent(routeKey, ignored -> {
            GeoCoordinate senderCoordinate = priceCoordinate(senderAddress);
            GeoCoordinate receiverCoordinate = priceCoordinate(receiverAddress);
            return (double) Math.round(osrmRouteService
                    .fetchRoute(senderCoordinate.latitude(), senderCoordinate.longitude(),
                            receiverCoordinate.latitude(), receiverCoordinate.longitude())
                    .durationSeconds() / 60.0);
        });
        return PricingFormula.calculate(size, weightValue, routeMinutes);
    }

    private GeoCoordinate priceCoordinate(String address) {
        return priceGeocodeCache.computeIfAbsent(priceAddressKey(address), ignored -> geocodingService.geocode(address));
    }

    private void setPricePreviewMessage(Label label, Label errorLabel, String message) {
        errorLabel.setText(message == null ? "" : message);
        label.setText("Cena finalna: ");
    }

    private void setPricePreviewPrice(Label label, Label errorLabel, BigDecimal price) {
        errorLabel.setText("");
        label.setText("Cena finalna: " + formatCurrencyValue(price) + " zł");
    }

    private String priceRouteKey(String senderAddress, String receiverAddress) {
        return priceAddressKey(senderAddress) + " -> " + priceAddressKey(receiverAddress);
    }

    private String priceAddressKey(String address) {
        return address == null ? "" : address.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Double parsePositiveWeight(String weightText) {
        try {
            double weightValue = Double.parseDouble((weightText == null ? "" : weightText).replace(",", "."));
            return weightValue > 0 ? weightValue : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String composeAddressSilently(AddressFields fields) {
        try {
            return composeAddress(fields, "podglądu");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String required(TextField field, String message) {
        String value = field.getText();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeSearch(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private TableView<Parcel> parcelTable() {
        TableView<Parcel> table = new TableView<>();
        table.getColumns().add(column("ID", item -> String.valueOf(item.getId()), 50));
        table.getColumns().add(column("Tytuł", Parcel::getTitle, 210));
        table.getColumns().add(column("Status", item -> item.getStatus().displayName(), 190));
        table.getColumns().add(column("Nadawca", Parcel::getSenderName, 160));
        table.getColumns().add(column("Odbiorca", Parcel::getReceiverName, 170));
        table.getColumns().add(column("Kurier", item -> nullToEmpty(item.getAssignedCourierName()), 180));
        table.getColumns().add(column("Cena", item -> formatCurrency(item.getEstimatedPrice()), 90));
        configureTable(table);
        return table;
    }

    private ParcelTableBundle parcelTableBundle() {
        TableView<Parcel> table = parcelTable();
        ObservableList<Parcel> source = FXCollections.observableArrayList();
        FilteredList<Parcel> filtered = new FilteredList<>(source, item -> true);
        SortedList<Parcel> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        TextField search = new TextField();
        search.setPromptText("Szukaj paczki, adresu, odbiorcy lub kuriera");
        search.setPrefWidth(360);
        ComboBox<ParcelStatus> status = new ComboBox<>(FXCollections.observableArrayList(visibleParcelStatuses()));
        status.setPromptText("Wszystkie statusy");
        status.setPrefWidth(220);
        stabilizeComboBox(status);
        Button clear = new Button("Wyczyść filtry");
        HBox filters = new HBox(8, search, status, clear);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.getStyleClass().add("filter-bar");
        search.textProperty().addListener((observable, oldValue, newValue) -> applyParcelFilter(filtered, search, status));
        status.valueProperty().addListener((observable, oldValue, newValue) -> applyParcelFilter(filtered, search, status));
        clear.setOnAction(event -> {
            search.clear();
            clearComboPrompt(status, "Wszystkie statusy");
        });
        return new ParcelTableBundle(table, source, filters);
    }

    private AddressTableBundle addressTableBundle() {
        TableView<ClientAddress> table = new TableView<>();
        table.getColumns().add(column("ID", item -> String.valueOf(item.getId()), 50));
        table.getColumns().add(column("Nazwa", ClientAddress::getName, 170));
        table.getColumns().add(column("Adres", ClientAddress::getAddressText, 640));
        table.getColumns().add(column("Uwagi", item -> nullToEmpty(item.getNotes()), 300));
        configureTable(table);
        ObservableList<ClientAddress> source = FXCollections.observableArrayList();
        FilteredList<ClientAddress> filtered = new FilteredList<>(source, item -> true);
        SortedList<ClientAddress> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        TextField search = new TextField();
        search.setPromptText("Szukaj ID, nazwy, adresu lub uwag");
        search.setPrefWidth(460);
        HBox filters = new HBox(8, search);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.getStyleClass().add("filter-bar");
        search.textProperty().addListener((observable, oldValue, newValue) -> applyAddressFilter(filtered, search));
        return new AddressTableBundle(table, source, filters);
    }

    private void applyParcelFilter(FilteredList<Parcel> filtered, TextField search, ComboBox<ParcelStatus> status) {
        String query = normalizeSearch(search.getText());
        ParcelStatus selectedStatus = status.getValue();
        filtered.setPredicate(parcel -> {
            boolean statusMatches = selectedStatus == null || parcel.getStatus() == selectedStatus;
            if (!statusMatches) {
                return false;
            }
            if (query.isBlank()) {
                return true;
            }
            return normalizeSearch(parcelSearchText(parcel)).contains(query);
        });
    }

    private String parcelSearchText(Parcel parcel) {
        return String.join(" ",
                String.valueOf(parcel.getId()),
                nullToEmpty(parcel.getTitle()),
                nullToEmpty(parcel.getSenderName()),
                nullToEmpty(parcel.getReceiverName()),
                nullToEmpty(parcel.getAssignedCourierName()),
                formatCurrency(parcel.getEstimatedPrice()),
                formatCurrencyValue(parcel.getEstimatedPrice()));
    }

    private void applyAddressFilter(FilteredList<ClientAddress> filtered, TextField search) {
        String query = normalizeSearch(search.getText());
        filtered.setPredicate(address -> query.isBlank()
                || normalizeSearch(addressSearchText(address)).contains(query));
    }

    private String addressSearchText(ClientAddress address) {
        return String.join(" ",
                String.valueOf(address.getId()),
                nullToEmpty(address.getName()),
                nullToEmpty(address.getAddressText()),
                nullToEmpty(address.getNotes()));
    }

    private record AddressFields(TextField street, TextField city, TextField postalCode,
                                 TextField buildingNumber, TextField apartmentNumber) {
    }

    private record AddressBookOptions(CheckBox useSaved, ComboBox<ClientAddress> savedAddress,
                                      CheckBox saveAddress, TextField addressName) {
    }

    private record ParcelTableBundle(TableView<Parcel> table, ObservableList<Parcel> source, HBox filters) {
    }

    private record AddressTableBundle(TableView<ClientAddress> table, ObservableList<ClientAddress> source, HBox filters) {
    }

    private record UserTableBundle(TableView<User> table, ObservableList<User> source, HBox filters) {
    }

    private record CourierTableBundle(TableView<Courier> table, ObservableList<Courier> source, HBox filters) {
    }

    private TableView<Courier> courierTable() {
        TableView<Courier> table = new TableView<>();
        table.getColumns().add(column("ID", item -> String.valueOf(item.getId()), 50));
        table.getColumns().add(column("Kurier", Courier::getFullName, 190));
        table.getColumns().add(column("Status", item -> item.getStatus().displayName(), 105));
        table.getColumns().add(column("Pojazd", Courier::getVehicleNumber, 95));
        table.getColumns().add(column("Zmiana", item -> item.getShiftStart() + "-" + item.getShiftEnd(), 115));
        table.getColumns().add(column("Test", item -> item.isTestModeEnabled() ? "Tak" : "Nie", 70));
        table.getColumns().add(column("Lokalizacja", Courier::getCurrentPointName, 430));
        configureTable(table);
        return table;
    }

    private CourierTableBundle courierTableBundle() {
        TableView<Courier> table = courierTable();
        ObservableList<Courier> source = FXCollections.observableArrayList();
        FilteredList<Courier> filtered = new FilteredList<>(source, item -> true);
        SortedList<Courier> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        TextField search = new TextField();
        search.setPromptText("Szukaj ID, kuriera, pojazdu lub lokalizacji");
        search.setPrefWidth(360);
        ComboBox<CourierStatus> status = new ComboBox<>(FXCollections.observableArrayList(CourierStatus.values()));
        status.setPromptText("Wszystkie statusy");
        status.setPrefWidth(220);
        stabilizeComboBox(status);
        Button clear = new Button("Wyczyść filtry");
        HBox filters = new HBox(8, search, status, clear);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.getStyleClass().add("filter-bar");
        search.textProperty().addListener((observable, oldValue, newValue) -> applyCourierFilter(filtered, search, status));
        status.valueProperty().addListener((observable, oldValue, newValue) -> applyCourierFilter(filtered, search, status));
        clear.setOnAction(event -> {
            search.clear();
            clearComboPrompt(status, "Wszystkie statusy");
        });
        return new CourierTableBundle(table, source, filters);
    }

    private UserTableBundle userTableBundle() {
        TableView<User> table = new TableView<>();
        table.getColumns().add(column("ID", user -> String.valueOf(user.getId()), 50));
        table.getColumns().add(column("Imię i nazwisko", User::getFullName, 260));
        table.getColumns().add(column("Adres e-mail", User::getEmail, 330));
        table.getColumns().add(column("Rola", user -> user.getRole().displayName(), 140));
        table.getColumns().add(column("Blokada", user -> user.isBlocked() ? "Tak" : "Nie", 90));
        configureTable(table, 5);

        ObservableList<User> source = FXCollections.observableArrayList();
        FilteredList<User> filtered = new FilteredList<>(source, item -> true);
        SortedList<User> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        TextField search = new TextField();
        search.setPromptText("Szukaj użytkownika, adresu e-mail lub telefonu");
        search.setPrefWidth(360);
        ComboBox<UserRole> role = new ComboBox<>(FXCollections.observableArrayList(UserRole.values()));
        role.setPromptText("Wszystkie role");
        role.setPrefWidth(220);
        stabilizeComboBox(role);
        Button clear = new Button("Wyczyść filtry");
        HBox filters = new HBox(8, search, role, clear);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.getStyleClass().add("filter-bar");
        search.textProperty().addListener((observable, oldValue, newValue) -> applyUserFilter(filtered, search, role));
        role.valueProperty().addListener((observable, oldValue, newValue) -> applyUserFilter(filtered, search, role));
        clear.setOnAction(event -> {
            search.clear();
            clearComboPrompt(role, "Wszystkie role");
        });
        return new UserTableBundle(table, source, filters);
    }

    private void applyUserFilter(FilteredList<User> filtered, TextField search, ComboBox<UserRole> role) {
        String query = normalizeSearch(search.getText());
        UserRole selectedRole = role.getValue();
        filtered.setPredicate(user -> {
            boolean roleMatches = selectedRole == null || user.getRole() == selectedRole;
            if (!roleMatches) {
                return false;
            }
            if (query.isBlank()) {
                return true;
            }
            return normalizeSearch(user.getId() + " " + user.getFullName() + " " + user.getEmail() + " " + user.getPhone()
                    + " " + user.getRole().displayName()).contains(query);
        });
    }

    private void applyCourierFilter(FilteredList<Courier> filtered, TextField search, ComboBox<CourierStatus> status) {
        String query = normalizeSearch(search.getText());
        CourierStatus selectedStatus = status.getValue();
        filtered.setPredicate(courier -> {
            boolean statusMatches = selectedStatus == null || courier.getStatus() == selectedStatus;
            if (!statusMatches) {
                return false;
            }
            return query.isBlank() || normalizeSearch(courierSearchText(courier)).contains(query);
        });
    }

    private String courierSearchText(Courier courier) {
        return String.join(" ",
                String.valueOf(courier.getId()),
                nullToEmpty(courier.getFullName()),
                nullToEmpty(courier.getVehicleNumber()),
                nullToEmpty(courier.getCurrentPointName()));
    }

    private <T> TableColumn<T, String> column(String title, Function<T, String> mapper) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(mapper.apply(data.getValue())));
        column.setSortable(true);
        return column;
    }

    private <T> TableColumn<T, String> column(String title, Function<T, String> mapper, double prefWidth) {
        TableColumn<T, String> column = column(title, mapper);
        column.setPrefWidth(prefWidth);
        if ("ID".equals(title)) {
            column.setMinWidth(45);
            column.setMaxWidth(75);
            column.setComparator((left, right) -> Long.compare(parseLong(left), parseLong(right)));
        }
        return column;
    }

    private <T> void configureTable(TableView<T> table) {
        configureTable(table, 4);
    }

    private <T> void configureTable(TableView<T> table, int visibleRows) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(34);
        table.setMinHeight(34 * visibleRows + 38);
        table.setPrefHeight(34 * visibleRows + 38);
        table.getColumns().forEach(column -> column.setSortable(true));
        applyRoundedClip(table, 16);
    }

    private boolean isDataRowClick(javafx.scene.input.MouseEvent event) {
        if (!(event.getTarget() instanceof javafx.scene.Node node)) {
            return false;
        }
        while (node != null && !(node instanceof javafx.scene.control.TableRow<?>)) {
            node = node.getParent();
        }
        return node instanceof javafx.scene.control.TableRow<?> row && !row.isEmpty();
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void loadParcels(ParcelTableBundle bundle) {
        send(MessageType.LIST_PARCELS_REQUEST, new TokenRequest(session.token()),
                response -> bundle.source().setAll(session.apiClient().dataList(response, Parcel.class).stream()
                        .sorted(Comparator.comparingLong(Parcel::getId).reversed())
                        .toList()));
    }

    private void loadAddresses(AddressTableBundle bundle) {
        send(MessageType.LIST_ADDRESSES_REQUEST, new TokenRequest(session.token()),
                response -> bundle.source().setAll(session.apiClient().dataList(response, ClientAddress.class)
                        .stream()
                        .sorted(Comparator.comparingLong(ClientAddress::getId).reversed())
                        .toList()));
    }

    private void loadClientAddresses(Consumer<List<ClientAddress>> consumer) {
        send(MessageType.LIST_ADDRESSES_REQUEST, new TokenRequest(session.token()),
                response -> consumer.accept(session.apiClient().dataList(response, ClientAddress.class)));
    }

    private void loadCouriers(TableView<Courier> table, ComboBox<Courier> comboBox) {
        send(MessageType.LIST_COURIERS_REQUEST, new TokenRequest(session.token()), response -> {
            List<Courier> couriers = session.apiClient().dataList(response, Courier.class).stream()
                    .sorted(Comparator.comparingLong(Courier::getId).reversed())
                    .toList();
            if (table != null) {
                table.setItems(FXCollections.observableArrayList(couriers));
            }
            if (comboBox != null) {
                List<Courier> assignableCouriers = couriers.stream()
                        .filter(courier -> courier.getStatus() == CourierStatus.AVAILABLE)
                        .toList();
                comboBox.setItems(FXCollections.observableArrayList(assignableCouriers));
                if (!assignableCouriers.isEmpty()) {
                    comboBox.setPromptText("Wybierz kuriera");
                    comboBox.setDisable(false);
                    comboBox.getSelectionModel().selectFirst();
                } else {
                    comboBox.getSelectionModel().clearSelection();
                    comboBox.setPromptText("Brak dostępnego kuriera");
                    comboBox.setDisable(false);
                }
            }
        });
    }

    private void loadCouriers(CourierTableBundle bundle) {
        send(MessageType.LIST_COURIERS_REQUEST, new TokenRequest(session.token()),
                response -> bundle.source().setAll(session.apiClient().dataList(response, Courier.class).stream()
                        .sorted(Comparator.comparingLong(Courier::getId).reversed())
                        .toList()));
    }

    private void loadCourierProfiles(ObservableList<Courier> target, Runnable afterLoad) {
        send(MessageType.LIST_COURIERS_REQUEST, new TokenRequest(session.token()), response -> {
            target.setAll(session.apiClient().dataList(response, Courier.class).stream()
                    .sorted(Comparator.comparingLong(Courier::getId).reversed())
                    .toList());
            if (afterLoad != null) {
                afterLoad.run();
            }
        });
    }

    private String vehicleForUser(User user, List<Courier> couriers) {
        if (user == null || user.getRole() != UserRole.COURIER) {
            return "KR NEW";
        }
        return couriers.stream()
                .filter(courier -> courier.getUserId() == user.getId())
                .map(Courier::getVehicleNumber)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("KR NEW");
    }

    private String shiftStartForUser(User user, List<Courier> couriers) {
        return courierForUser(user, couriers)
                .map(Courier::getShiftStart)
                .filter(value -> value != null && !value.isBlank())
                .orElse("06:00");
    }

    private String shiftEndForUser(User user, List<Courier> couriers) {
        return courierForUser(user, couriers)
                .map(Courier::getShiftEnd)
                .filter(value -> value != null && !value.isBlank())
                .orElse("14:00");
    }

    private boolean courierTestModeForUser(User user, List<Courier> couriers) {
        return courierForUser(user, couriers)
                .map(Courier::isTestModeEnabled)
                .orElse(false);
    }

    private Optional<Courier> courierForUser(User user, List<Courier> couriers) {
        if (user == null || user.getRole() != UserRole.COURIER) {
            return Optional.empty();
        }
        return couriers.stream()
                .filter(courier -> courier.getUserId() == user.getId())
                .findFirst();
    }

    private void loadUsers(UserTableBundle bundle) {
        send(MessageType.LIST_USERS_REQUEST, new TokenRequest(session.token()),
                response -> bundle.source().setAll(session.apiClient().dataList(response, User.class).stream()
                        .sorted(Comparator.comparingLong(User::getId).reversed())
                        .toList()));
    }

    private void loadEvents(ListView<String> list) {
        send(MessageType.LIST_EVENTS_REQUEST, new TokenRequest(session.token()), response -> {
            List<EventLog> events = session.apiClient().dataList(response, EventLog.class);
            list.setItems(FXCollections.observableArrayList(events.stream()
                    .map(event -> TimeUtil.formatDisplay(event.getCreatedAt()) + " [" + event.getLevel() + "] " + event.getMessage())
                    .toList()));
        });
    }

    private void loadReports(ListView<String> reports) {
        send(MessageType.LIST_REPORTS_REQUEST, new TokenRequest(session.token()),
                response -> reports.setItems(FXCollections.observableArrayList(session.apiClient().dataList(response, String.class))));
    }

    private void openSelectedReport(ListView<String> reports) {
        String selected = reports.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiDialogs.error("Wybierz raport.");
            return;
        }
        send(MessageType.READ_REPORT_REQUEST, new ReportRequest(session.token(), selected), response ->
                UiDialogs.text(selected, session.apiClient().data(response, String.class)));
    }

    private void showParcelDetails(Parcel parcel) {
        if (parcel == null) {
            UiDialogs.error("Wybierz paczkę.");
            return;
        }
        send(MessageType.PARCEL_HISTORY_REQUEST, new IdRequest(session.token(), parcel.getId()), response -> {
            List<ParcelStatusHistory> history = session.apiClient().dataList(response, ParcelStatusHistory.class);
            UiDialogs.custom("Szczegóły paczki #" + parcel.getId(), parcelDetailsView(parcel, history), 1080, 820, "parcel-details-dialog");
        });
    }

    private javafx.scene.Node parcelDetailsView(Parcel parcel, List<ParcelStatusHistory> history) {
        String courier = isBlank(parcel.getAssignedCourierName()) ? "brak przypisanego kuriera" : parcel.getAssignedCourierName();
        String courierPhone = isBlank(parcel.getAssignedCourierPhone()) ? "brak telefonu" : parcel.getAssignedCourierPhone();
        VBox root = new VBox(12);
        root.getStyleClass().add("parcel-details-root");

        Label title = new Label("Szczegóły paczki #" + parcel.getId());
        title.getStyleClass().add("parcel-details-title");
        Label badge = new Label("Paczka ID: #" + parcel.getId());
        badge.getStyleClass().add("parcel-details-badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(12, title, spacer, badge);
        heading.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12);
        content.getStyleClass().add("parcel-details-content");
        content.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(parcelStatusBanner(parcel));

        VBox leftColumn = new VBox(12,
                parcelCard("Paczka #" + parcel.getId() + " - " + nullToEmpty(parcel.getTitle()), "\uD83D\uDCE6",
                        detailRow("Rozmiar", parcel.getSize() == null ? "" : parcel.getSize().displayName()),
                        detailRow("Wymiary", parcel.getSize() == null ? "" : parcel.getSize().dimensions()),
                        detailRow("Waga", parcel.getWeightKg() + " kg"),
                        detailRow("Cena", formatCurrency(parcel.getEstimatedPrice())),
                        detailRow("Kurier", courier),
                        detailRow("Telefon kuriera", courierPhone)),
                parcelCard("Nadawca", "\uD83D\uDC64",
                        detailRow("Imię i nazwisko", nullToEmpty(parcel.getSenderName())),
                        detailRow("Telefon", nullToEmpty(parcel.getSenderPhone())),
                        detailRow("Adres", nullToEmpty(parcel.getSenderAddressText()))),
                parcelCard("Odbiorca", "\uD83C\uDFE2",
                        detailRow("Imię i nazwisko", nullToEmpty(parcel.getReceiverName())),
                        detailRow("Telefon", nullToEmpty(parcel.getReceiverPhone())),
                        detailRow("Adres", nullToEmpty(parcel.getReceiverAddressText()))));
        VBox rightColumn = new VBox(12, parcelHistoryCard(history));
        leftColumn.getStyleClass().add("parcel-details-column");
        rightColumn.getStyleClass().add("parcel-details-column");
        leftColumn.setMinWidth(0);
        leftColumn.setPrefWidth(0);
        rightColumn.setMinWidth(0);
        rightColumn.setPrefWidth(0);
        HBox.setHgrow(leftColumn, Priority.ALWAYS);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);
        HBox columns = new HBox(14, leftColumn, rightColumn);
        columns.getStyleClass().add("parcel-details-columns");
        columns.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(columns);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(660);
        scrollPane.getStyleClass().add("parcel-details-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().addAll(heading, scrollPane);
        return root;
    }

    private HBox parcelStatusBanner(Parcel parcel) {
        Label icon = new Label(parcel.getStatus() == ParcelStatus.DELIVERED ? "✓" : "•");
        icon.getStyleClass().add("parcel-details-status-icon");
        Label status = new Label("Status: " + (parcel.getStatus() == null ? "" : parcel.getStatus().displayName()));
        status.getStyleClass().add("parcel-details-status-label");
        HBox banner = new HBox(18, icon, status);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().add("parcel-details-status-banner");
        banner.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(status, Priority.ALWAYS);
        return banner;
    }

    private VBox parcelCard(String title, String iconText, javafx.scene.Node... rows) {
        Label icon = new Label(iconText);
        icon.getStyleClass().add("parcel-details-card-icon");
        Label label = new Label(title);
        label.getStyleClass().add("parcel-details-card-title");
        HBox heading = new HBox(12, icon, label);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(9, heading);
        box.getStyleClass().add("parcel-details-card");
        box.getChildren().addAll(rows);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private HBox detailRow(String labelText, String valueText) {
        Label label = new Label(labelText + ":");
        label.getStyleClass().add("parcel-details-field-label");
        label.setMinWidth(128);
        label.setPrefWidth(128);
        Label value = new Label(isBlank(valueText) ? "-" : valueText);
        value.getStyleClass().add("parcel-details-field-value");
        value.setWrapText(true);
        value.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(8, label, value);
        row.getStyleClass().add("parcel-details-row");
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(value, Priority.ALWAYS);
        return row;
    }

    private VBox parcelHistoryCard(List<ParcelStatusHistory> history) {
        List<ParcelStatusHistory> timeline = history == null ? List.of() : history.stream()
                .sorted(Comparator.comparing(ParcelStatusHistory::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        VBox card = parcelCard("Historia statusów", "\u23F2");
        card.getStyleClass().add("parcel-details-history-card");
        VBox.setVgrow(card, Priority.ALWAYS);
        if (timeline.isEmpty()) {
            Label empty = new Label("Brak historii statusów.");
            empty.getStyleClass().add("parcel-details-muted");
            card.getChildren().add(empty);
            return card;
        }
        VBox rows = new VBox(0);
        rows.getStyleClass().add("parcel-details-timeline");
        for (int i = 0; i < timeline.size(); i++) {
            rows.getChildren().add(parcelHistoryRow(timeline.get(i), i == timeline.size() - 1));
        }
        card.getChildren().add(rows);
        return card;
    }

    private HBox parcelHistoryRow(ParcelStatusHistory item, boolean last) {
        Label date = new Label(TimeUtil.formatDisplay(item.getCreatedAt()));
        date.getStyleClass().add("parcel-details-timeline-date");
        Text statusLabel = new Text("Status: ");
        statusLabel.getStyleClass().add("parcel-details-field-label");
        Text statusValue = new Text(item.getStatus() == null ? "" : item.getStatus().displayName());
        statusValue.getStyleClass().add("parcel-details-field-value");
        TextFlow status = new TextFlow(statusLabel, statusValue);
        Text noteLabel = new Text("Notatka: ");
        noteLabel.getStyleClass().add("parcel-details-field-label");
        Text noteValue = new Text(isBlank(item.getNote()) ? "-" : item.getNote());
        noteValue.getStyleClass().add("parcel-details-field-value");
        TextFlow note = new TextFlow(noteLabel, noteValue);
        note.setMaxWidth(Double.MAX_VALUE);
        VBox text = new VBox(2, date, status, note);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(12, timelineRail(last), text);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("parcel-details-timeline-row");
        return row;
    }

    private VBox timelineRail(boolean last) {
        Circle dot = new Circle(last ? 6 : 5);
        dot.getStyleClass().add(last ? "parcel-details-timeline-dot-current" : "parcel-details-timeline-dot");
        Line line = new Line(0, 0, 0, 60);
        line.getStyleClass().add("parcel-details-timeline-line");
        line.setVisible(!last);
        line.setManaged(!last);
        VBox rail = new VBox(0, dot, line);
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setMinWidth(28);
        rail.setPrefWidth(28);
        return rail;
    }

    private javafx.scene.Node createParcelSuccessView(AssignmentResult result) {
        Parcel parcel = result.parcel();
        String courier = result.courier() == null ? "" : result.courier().getFullName();
        String courierFirstName = isBlank(courier) ? "brak przypisanego kuriera" : firstName(courier);
        String courierLastName = isBlank(courier) ? "-" : lastName(courier);
        String courierPhone = result.courier() == null || isBlank(result.courier().getPhone())
                ? "brak przypisanego kuriera"
                : result.courier().getPhone();

        Label title = new Label("Operacja zakończona powodzeniem");
        title.getStyleClass().add("parcel-success-title");

        HBox banner = parcelSuccessBanner();
        HBox cards = new HBox(18,
                parcelSuccessCard("\uD83D\uDCE6", "Paczka",
                        parcelSuccessLine("Numer", "#" + parcel.getId()),
                        parcelSuccessLine("Tytuł", nullToEmpty(parcel.getTitle()))),
                parcelSuccessCard("\uD83D\uDC64", "Kurier",
                        parcelSuccessLine("Imię", courierFirstName),
                        parcelSuccessLine("Nazwisko", courierLastName),
                        parcelSuccessLine("Telefon", courierPhone)),
                parcelSuccessCard("\uD83C\uDFF7", "Cena",
                        parcelSuccessValue(formatCurrency(parcel.getEstimatedPrice())))
        );
        cards.getStyleClass().add("parcel-success-cards");
        cards.setAlignment(Pos.TOP_CENTER);

        VBox root = new VBox(20, title, banner, cards);
        root.getStyleClass().add("parcel-success-root");
        return root;
    }

    private HBox parcelSuccessBanner() {
        Label icon = new Label("\u2713");
        icon.getStyleClass().add("parcel-success-check");
        Label title = new Label("Paczka została nadana");
        title.getStyleClass().add("parcel-success-banner-title");
        Label message = new Label("Zlecenie zostało poprawnie zapisane w systemie.");
        message.getStyleClass().add("parcel-success-banner-message");
        VBox text = new VBox(7, title, message);
        HBox banner = new HBox(20, icon, text);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().add("parcel-success-banner");
        HBox.setHgrow(text, Priority.ALWAYS);
        return banner;
    }

    private VBox parcelSuccessCard(String iconText, String titleText, javafx.scene.Node... rows) {
        Label icon = new Label(iconText);
        icon.getStyleClass().add("parcel-success-icon");
        Label title = new Label(titleText);
        title.getStyleClass().add("parcel-success-card-title");
        Separator separator = new Separator();
        separator.getStyleClass().add("parcel-success-divider");
        VBox body = new VBox(7, rows);
        body.getStyleClass().add("parcel-success-card-body");
        body.setAlignment(Pos.TOP_CENTER);
        body.setMaxWidth(Double.MAX_VALUE);
        VBox card = new VBox(10, icon, title, separator, body);
        card.setAlignment(Pos.TOP_CENTER);
        card.getStyleClass().add("parcel-success-card");
        card.setMinWidth(0);
        card.setPrefWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private HBox parcelSuccessLine(String labelText, String valueText) {
        Label label = new Label(labelText + ":");
        label.getStyleClass().add("parcel-success-label");
        label.setAlignment(Pos.CENTER_RIGHT);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setMinWidth(Region.USE_PREF_SIZE);
        Label value = new Label(isBlank(valueText) ? "-" : valueText);
        value.getStyleClass().add("parcel-success-value");
        value.setWrapText(false);
        value.setTextOverrun(OverrunStyle.ELLIPSIS);
        value.setAlignment(Pos.CENTER_LEFT);
        value.setTextAlignment(TextAlignment.CENTER);
        value.setMaxWidth(PARCEL_SUCCESS_VALUE_MAX_WIDTH);
        HBox row = new HBox(5, label, value);
        row.getStyleClass().add("parcel-success-line");
        row.setAlignment(Pos.CENTER);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private Label parcelSuccessValue(String valueText) {
        Label value = new Label(isBlank(valueText) ? "-" : valueText);
        value.getStyleClass().add("parcel-success-standalone-value");
        value.getStyleClass().add("parcel-success-price-value");
        value.setWrapText(false);
        value.setTextOverrun(OverrunStyle.ELLIPSIS);
        value.setAlignment(Pos.CENTER);
        value.setTextAlignment(TextAlignment.CENTER);
        value.setMaxWidth(Double.MAX_VALUE);
        return value;
    }

    private String firstName(String fullName) {
        String normalized = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
        int separator = normalized.indexOf(' ');
        return separator < 0 ? normalized : normalized.substring(0, separator);
    }

    private String lastName(String fullName) {
        String normalized = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
        int separator = normalized.indexOf(' ');
        return separator < 0 || separator + 1 >= normalized.length() ? "-" : normalized.substring(separator + 1);
    }

    private void confirmDuplicateParcelTitle(String title, Runnable onConfirmed) {
        String normalizedTitle = normalizeTitle(title);
        send(MessageType.LIST_PARCELS_REQUEST, new TokenRequest(session.token()), response -> {
            boolean duplicate = session.apiClient().dataList(response, Parcel.class).stream()
                    .anyMatch(parcel -> normalizeTitle(parcel.getTitle()).equals(normalizedTitle)
                            && isActiveParcel(parcel.getStatus()));
            if (!duplicate || UiDialogs.confirm("Masz już aktywną paczkę o takiej nazwie. Czy na pewno utworzyć kolejną?")) {
                onConfirmed.run();
            }
        });
    }

    private boolean isActiveParcel(ParcelStatus status) {
        return status != ParcelStatus.DELIVERED
                && status != ParcelStatus.CANCELED
                && status != ParcelStatus.DELIVERY_PROBLEM;
    }

    private String normalizeTitle(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private boolean sameAddress(String first, String second) {
        return normalizeAddressText(first).equals(normalizeAddressText(second));
    }

    private String normalizeAddressText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void send(MessageType type, Object payload, Consumer<ProtocolMessage> success) {
        session.apiClient().send(type, payload)
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        UiDialogs.error(errorText(error));
                        return;
                    }
                    success.accept(response);
                }));
    }

    private String errorText(Throwable error) {
        Throwable current = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return current.getMessage() == null ? "Wystąpił błąd." : current.getMessage();
    }

    private GridPane form() {
        GridPane form = new GridPane();
        form.setHgap(18);
        form.setVgap(14);
        form.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(150);
        labelColumn.setPrefWidth(170);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().setAll(labelColumn, fieldColumn);
        return form;
    }

    private Label add(GridPane form, int row, String label, javafx.scene.Node node) {
        Label text = new Label(label);
        form.add(text, 0, row);
        form.add(node, 1, row);
        if (node instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(region, Priority.ALWAYS);
        }
        return text;
    }

    private void bindVisibility(javafx.scene.Node node, javafx.beans.value.ObservableValue<Boolean> visible) {
        node.visibleProperty().bind(visible);
        node.managedProperty().bind(node.visibleProperty());
    }

    private VBox settingsSection(String title, javafx.scene.Node content) {
        Label label = new Label(title);
        label.getStyleClass().add("subsection-title");
        VBox section = new VBox(10, label, content);
        section.getStyleClass().add("settings-section");
        section.setMaxWidth(Double.MAX_VALUE);
        return section;
    }

    private VBox settingsSection(String title, javafx.scene.Node content, Button action) {
        VBox section = settingsSection(title, content);
        action.getStyleClass().add("settings-action-button");
        HBox actions = new HBox(action);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("section-actions");
        section.getChildren().add(actions);
        return section;
    }

    private HBox splitToolbar(HBox left, HBox right) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8);
        toolbar.getChildren().setAll(left, spacer, right);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("table-toolbar");
        return toolbar;
    }

    private HBox tableTopBar(HBox left, HBox right) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, left, spacer, right);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("filter-bar");
        return toolbar;
    }

    private HBox selectedActions(javafx.scene.Node... nodes) {
        HBox box = new HBox(8, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("selected-actions");
        return box;
    }

    private Separator tableSeparator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("table-separator");
        return separator;
    }

    private VBox singleField(String label, javafx.scene.Node field) {
        Label text = new Label(label);
        if (field instanceof Region region) {
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
        VBox box = new VBox(6, text, field);
        box.setMinWidth(0);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private HBox twoColumnRow(String leftLabel, javafx.scene.Node leftField,
                              String rightLabel, javafx.scene.Node rightField) {
        VBox left = singleField(leftLabel, leftField);
        VBox right = singleField(rightLabel, rightField);
        left.setMinWidth(0);
        left.setPrefWidth(0);
        right.setMinWidth(0);
        right.setPrefWidth(0);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox row = new HBox(12, left, right);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private <T> void stabilizeComboBox(ComboBox<T> comboBox) {
        comboBox.setMinWidth(0);
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.setVisibleRowCount(8);
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("combo-placeholder-cell");
                if (empty || item == null) {
                    setText(comboBox.getPromptText());
                    getStyleClass().add("combo-placeholder-cell");
                } else {
                    setText(item.toString());
                }
            }
        });
    }

    private <T> void clearComboPrompt(ComboBox<T> comboBox, String promptText) {
        comboBox.setPromptText(promptText);
        comboBox.getSelectionModel().clearSelection();
        comboBox.setValue(null);
        Platform.runLater(comboBox::requestLayout);
    }

    private List<ParcelStatus> courierSelectableParcelStatuses() {
        return Arrays.stream(ParcelStatus.values())
                .filter(status -> status != ParcelStatus.CANCELED)
                .filter(status -> status != ParcelStatus.WAITING_FOR_COURIER)
                .filter(status -> status != ParcelStatus.WAREHOUSE)
                .toList();
    }

    private List<ParcelStatus> visibleParcelStatuses() {
        return Arrays.stream(ParcelStatus.values())
                .filter(status -> status != ParcelStatus.WAREHOUSE)
                .toList();
    }

    private boolean isTerminalStatus(ParcelStatus status) {
        return status == ParcelStatus.CANCELED
                || status == ParcelStatus.DELIVERED
                || status == ParcelStatus.DELIVERY_PROBLEM;
    }

    private boolean canAssignCourierFromPanel(Parcel parcel) {
        if (parcel == null || isTerminalStatus(parcel.getStatus())) {
            return false;
        }
        return parcel.getStatus() == ParcelStatus.WAITING_FOR_COURIER
                || parcel.getStatus() == ParcelStatus.WAREHOUSE;
    }

    private ScrollPane scrollContent(javafx.scene.Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("content-scroll");
        return scrollPane;
    }

    private void styleDialog(Dialog<?> dialog) {
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        String stylesheet = session.apiClient().cssFor(session.user().getTheme());
        if (!dialog.getDialogPane().getStylesheets().contains(stylesheet)) {
            dialog.getDialogPane().getStylesheets().add(stylesheet);
        }
        dialog.setResizable(true);
        dialog.getDialogPane().setMaxWidth(Double.MAX_VALUE);
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        if (owner != null && owner.isShowing()) {
            dialog.initOwner(owner);
        }
        dialog.setOnShown(event -> {
            if (LogoAssets.appIconImage() != null
                    && dialog.getDialogPane().getScene() != null
                    && dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
                stage.getIcons().setAll(LogoAssets.appIconImage());
                keepDialogOnScreen(stage, stage.getOwner());
            } else if (dialog.getDialogPane().getScene() != null
                    && dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
                keepDialogOnScreen(stage, stage.getOwner());
            }
        });
    }

    private void keepDialogOnScreen(Stage stage, Window owner) {
        Window reference = owner == null ? stage : owner;
        Rectangle2D bounds = Screen.getScreensForRectangle(reference.getX(), reference.getY(),
                        reference.getWidth(), reference.getHeight())
                .stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();
        double maxWidth = Math.max(320, bounds.getWidth() - DIALOG_SCREEN_MARGIN);
        double maxHeight = Math.max(260, bounds.getHeight() - DIALOG_SCREEN_MARGIN);
        if (stage.getWidth() > maxWidth) {
            stage.setWidth(maxWidth);
        }
        if (stage.getHeight() > maxHeight) {
            stage.setHeight(maxHeight);
        }
        double centerX = owner == null
                ? bounds.getMinX() + (bounds.getWidth() - stage.getWidth()) / 2
                : owner.getX() + (owner.getWidth() - stage.getWidth()) / 2;
        double centerY = owner == null
                ? bounds.getMinY() + (bounds.getHeight() - stage.getHeight()) / 2
                : owner.getY() + (owner.getHeight() - stage.getHeight()) / 2;
        stage.setX(clamp(centerX, bounds.getMinX(), Math.max(bounds.getMinX(), bounds.getMaxX() - stage.getWidth())));
        stage.setY(clamp(centerY, bounds.getMinY(), Math.max(bounds.getMinY(), bounds.getMaxY() - stage.getHeight())));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private void setWide(TextField... fields) {
        for (TextField field : fields) {
            field.setPrefWidth(520);
            field.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private VBox wrap(String title, javafx.scene.Node... nodes) {
        Label label = new Label(title);
        label.getStyleClass().add("section-title");
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("panel");
        box.getChildren().add(label);
        box.getChildren().addAll(nodes);
        for (javafx.scene.Node node : nodes) {
            if (node instanceof TableView<?> || node instanceof ListView<?>
                    || node instanceof WebView || node instanceof ScrollPane || node instanceof StackPane) {
                VBox.setVgrow(node, Priority.ALWAYS);
            }
            if (node instanceof TableView<?> || node instanceof ListView<?>) {
                applyRoundedClip((Region) node, 16);
            }
        }
        return box;
    }

    private void applyRoundedClip(Region region, double radius) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        region.setClip(clip);
    }

    private VBox wrap(javafx.scene.Node... nodes) {
        VBox box = new VBox(12, nodes);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("panel");
        return box;
    }

    private void setContent(javafx.scene.Node node) {
        disposeActiveContent();
        activeDisposableContent = node instanceof DisposableContent disposable ? disposable : null;
        StackPane.setMargin(node, new Insets(0, 24, 24, 24));
        content.getChildren().setAll(node);
    }

    private void disposeActiveContent() {
        if (activeDisposableContent != null) {
            activeDisposableContent.dispose();
            activeDisposableContent = null;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
