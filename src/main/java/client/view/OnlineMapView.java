package client.view;

import client.controller.SessionContext;
import client.map.GeoCoordinate;
import client.map.OnlineMapJavaConnector;
import client.map.OsrmRoute;
import client.map.OsrmRouteService;
import client.map.RoutePreviewPolicy;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Tooltip;
import javafx.geometry.Orientation;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import model.Courier;
import model.MapPoint;
import model.Parcel;
import model.ParcelStatus;
import model.UserRole;
import netscape.javascript.JSObject;
import protocol.MessageType;
import protocol.ProtocolJson;
import protocol.requests.TokenRequest;
import protocol.responses.FleetSnapshotEntry;
import protocol.responses.FleetTaskPhase;
import util.AppConfig;
import util.TimeUtil;

import java.text.Normalizer;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class OnlineMapView extends VBox implements DisposableContent {
    private static final double SIDEBAR_WIDTH = 390;
    private static final int MAX_PENDING_MAP_COMMANDS = 120;
    private static final int MIN_REFRESH_SECONDS = 2;
    private static final Comparator<CourierLiveEntry> COURIER_LIST_ORDER =
            Comparator.comparingInt(OnlineMapView::parcelAssignmentGroup)
                    .thenComparingInt(OnlineMapView::parcelDeliveryProgress)
                    .thenComparingInt(entry -> entry.phase().priority())
                    .thenComparing(entry -> entry.courier().getFullName(), String.CASE_INSENSITIVE_ORDER);

    private final SessionContext session;
    private final AppConfig config;
    private final OsrmRouteService osrmRouteService;
    private final ExecutorService executor;
    private final Timeline refreshTimeline;
    private final PauseTransition resizeDebounce = new PauseTransition(Duration.millis(160));
    private final WebView webView = new WebView();
    private final WebEngine webEngine = webView.getEngine();
    private final ListView<CourierLiveEntry> courierList = new ListView<>();
    private final Label routeStatus = new Label("Wybierz kuriera, aby zobaczyć jego bieżącą trasę.");
    private final Label routeMeta = new Label("Tutaj pojawi się cel kuriera, dystans pozostały do przejazdu i przybliżony czas dotarcia.");
    private final Label routeDetails = new Label("");
    private final Label fleetStatus = new Label("Ładowanie pozycji kurierów...");
    private final Button sidebarToggle = new Button(">");
    private final ArrayDeque<String> pendingMapCommands = new ArrayDeque<>();
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private final AtomicLong routePreviewVersion = new AtomicLong();
    private final Map<Long, MapPoint> mapPointsById = new HashMap<>();

    private boolean mapReady;
    private boolean restoringSelection;
    private boolean mapSidebarCollapsed;
    private String lastRoutePreviewKey = "";
    private String lastRouteScopeKey = "";
    private String lastRouteMetaText = "";
    private String lastRouteDetailsText = "";
    private String routeRequestInFlightKey = "";
    private Long selectedCourierId;
    private List<GeoCoordinate> lastRouteGeometry = List.of();
    private List<CourierLiveEntry> currentEntries = List.of();

    public OnlineMapView(SessionContext session) {
        this(session, new AppConfig());
    }

    private OnlineMapView(SessionContext session, AppConfig config) {
        this(session, config, new OsrmRouteService(config));
    }

    OnlineMapView(SessionContext session, AppConfig config, OsrmRouteService osrmRouteService) {
        this.session = Objects.requireNonNull(session);
        this.config = Objects.requireNonNull(config);
        this.osrmRouteService = Objects.requireNonNull(osrmRouteService);
        this.executor = Executors.newFixedThreadPool(3, daemonThreadFactory());
        this.refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(refreshSeconds()),
                event -> refreshData()));
        this.refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        build();
        configureMapBridge();
        loadMap();
        loadMapPoints();
        refreshData();
        refreshTimeline.play();
    }

    private void build() {
        getStyleClass().add("panel");
        getStyleClass().add("online-map-root");
        setPadding(new Insets(0));
        setSpacing(0);

        routeStatus.getStyleClass().add("online-map-status");
        routeMeta.getStyleClass().add("online-map-meta");
        routeDetails.getStyleClass().add("muted");
        fleetStatus.getStyleClass().add("muted");
        routeStatus.setWrapText(true);
        routeMeta.setWrapText(true);
        routeDetails.setWrapText(true);
        fleetStatus.setWrapText(true);
        prepareWrappingLabel(routeStatus);
        prepareWrappingLabel(routeMeta);
        prepareWrappingLabel(routeDetails);
        prepareWrappingLabel(fleetStatus);

        Button focusSelected = new Button("Wyśrodkuj kuriera");
        Button resetView = new Button("Pokaż cały Kraków");
        Button clearPreview = new Button("Wyczyść podgląd");
        Button refreshFleet = new Button("Odśwież teraz");
        focusSelected.getStyleClass().add("compact-button");
        resetView.getStyleClass().add("secondary-button");
        clearPreview.getStyleClass().add("secondary-button");
        refreshFleet.getStyleClass().add("compact-button");

        focusSelected.setOnAction(event -> focusSelectedCourier());
        resetView.setOnAction(event -> resetMapView());
        clearPreview.setOnAction(event -> clearRoutePreview());
        refreshFleet.setOnAction(event -> refreshData());

        VBox routeSection = new VBox(10,
                subsection("Aktywna trasa"),
                actionRow(focusSelected, resetView),
                routeStatus,
                routeMeta,
                routeDetails
        );
        routeSection.getStyleClass().add("form-section");
        routeSection.getStyleClass().add("online-map-glass-card");

        courierList.setPlaceholder(new Label("Brak widocznych kurierów."));
        courierList.setFocusTraversable(false);
        courierList.setCellFactory(list -> new CourierLiveCell(session.user().getId()));
        courierList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) ->
                onCourierSelected(selected));
        courierList.setPrefHeight(360);
        applyRoundedClip(courierList, 16);
        VBox.setVgrow(courierList, Priority.ALWAYS);

        VBox fleetSection = new VBox(10,
                subsection(session.user().getRole() == UserRole.DISPATCHER ? "Flota na żywo" : "Pozycje kurierów"),
                fleetStatus,
                actionRow(refreshFleet, clearPreview),
                courierList
        );
        fleetSection.getStyleClass().add("form-section");
        fleetSection.getStyleClass().add("online-map-glass-card");
        VBox.setVgrow(fleetSection, Priority.ALWAYS);

        VBox sidebar = new VBox(12, routeSection, fleetSection);
        sidebar.getStyleClass().add("online-map-sidebar");
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        sidebar.setMinWidth(340);
        sidebar.setMaxWidth(SIDEBAR_WIDTH);
        VBox.setVgrow(sidebar, Priority.ALWAYS);

        sidebarToggle.getStyleClass().add("online-map-sidebar-toggle");
        sidebarToggle.setTooltip(new Tooltip("Schowaj panel mapy"));
        sidebarToggle.setFocusTraversable(false);
        sidebarToggle.setOnAction(event -> setMapSidebarCollapsed(sidebar, !mapSidebarCollapsed));

        webView.getStyleClass().add("online-map-webview");
        webView.setContextMenuEnabled(false);
        webView.setMinSize(0, 0);
        webView.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        StackPane mapHost = new StackPane(webView);
        mapHost.getStyleClass().add("online-map-host");
        mapHost.setMinHeight(540);
        mapHost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        applyRoundedClip(mapHost, 18);
        webView.prefWidthProperty().bind(mapHost.widthProperty());
        webView.prefHeightProperty().bind(mapHost.heightProperty());
        mapHost.widthProperty().addListener((observable, oldValue, newValue) -> requestMapResize());
        mapHost.heightProperty().addListener((observable, oldValue, newValue) -> requestMapResize());
        VBox.setVgrow(mapHost, Priority.ALWAYS);

        StackPane mapStage = new StackPane(mapHost, sidebar, sidebarToggle);
        mapStage.getStyleClass().add("online-map-stage");
        mapStage.setAlignment(Pos.CENTER);
        applyRoundedClip(mapStage, 18);
        StackPane.setAlignment(sidebar, Pos.TOP_RIGHT);
        StackPane.setMargin(sidebar, new Insets(70, 18, 28, 0));
        StackPane.setAlignment(sidebarToggle, Pos.TOP_RIGHT);
        StackPane.setMargin(sidebarToggle, new Insets(18, 18, 0, 0));
        VBox.setVgrow(mapStage, Priority.ALWAYS);

        getChildren().add(mapStage);
    }

    private void setMapSidebarCollapsed(VBox sidebar, boolean collapsed) {
        mapSidebarCollapsed = collapsed;
        sidebar.setVisible(!collapsed);
        sidebar.setManaged(!collapsed);
        sidebar.setMouseTransparent(collapsed);
        sidebarToggle.setText(collapsed ? "<" : ">");
        sidebarToggle.setTooltip(new Tooltip(collapsed ? "Pokaż panel mapy" : "Schowaj panel mapy"));
        executeMapCommand("setMapPanelCollapsed(" + (collapsed ? "true" : "false") + ");");
        requestMapResize();
    }

    private void applyRoundedClip(Region region, double radius) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        region.setClip(clip);
    }

    private int refreshSeconds() {
        return Math.max(MIN_REFRESH_SECONDS, config.onlineMapRefreshSeconds());
    }

    private void prepareWrappingLabel(Label label) {
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setTextOverrun(OverrunStyle.CLIP);
    }

    private void configureMapBridge() {
        OnlineMapJavaConnector connector = new OnlineMapJavaConnector(
                this::onMapReady,
                this::onMapCourierSelected);
        webEngine.getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("javaConnector", connector);
                webEngine.executeScript("if (window.onJavaConnectorReady) { window.onJavaConnectorReady(); }");
            }
        });
    }

    private void loadMap() {
        URL resource = getClass().getResource("/map.html");
        if (resource == null) {
            routeStatus.setText("Nie znaleziono zasobu mapy.");
            return;
        }
        webEngine.load(resource.toExternalForm());
    }

    private void loadMapPoints() {
        session.apiClient().send(MessageType.LIST_MAP_POINTS_REQUEST, new TokenRequest(session.token()))
                .thenApply(response -> session.apiClient().dataList(response, MapPoint.class))
                .whenComplete((points, error) -> Platform.runLater(() -> {
                    if (disposed.get() || error != null || points == null) {
                        return;
                    }
                    mapPointsById.clear();
                    for (MapPoint point : points) {
                        mapPointsById.put(point.getId(), point);
                    }
                    syncWarehouseMarkers();
                }));
    }

    private void refreshData() {
        if (disposed.get()) {
            return;
        }
        if (!refreshInProgress.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture<List<FleetSnapshotEntry>> fleetFuture = session.apiClient()
                .send(MessageType.LIST_FLEET_SNAPSHOT_REQUEST, new TokenRequest(session.token()))
                .thenApply(response -> session.apiClient().dataList(response, FleetSnapshotEntry.class));
        CompletableFuture<List<MapPoint>> pointsFuture = mapPointsById.isEmpty()
                ? session.apiClient().send(MessageType.LIST_MAP_POINTS_REQUEST, new TokenRequest(session.token()))
                .thenApply(response -> session.apiClient().dataList(response, MapPoint.class))
                : CompletableFuture.completedFuture(new ArrayList<>(mapPointsById.values()));

        fleetFuture
                .thenCombine(pointsFuture, DataSnapshot::new)
                .thenCompose(snapshot -> CompletableFuture.supplyAsync(() -> buildEntries(snapshot), executor))
                .whenComplete((entries, error) -> Platform.runLater(() -> {
                    try {
                        if (disposed.get()) {
                            return;
                        }
                        if (error != null) {
                            fleetStatus.setText(errorText(error));
                            return;
                        }
                        updateCourierUi(entries);
                    } finally {
                        refreshInProgress.set(false);
                    }
                }));
    }

    private List<CourierLiveEntry> buildEntries(DataSnapshot snapshot) {
        Map<Long, MapPoint> points = new HashMap<>();
        for (MapPoint point : snapshot.points()) {
            points.put(point.getId(), point);
        }
        List<CourierLiveEntry> entries = new ArrayList<>();
        for (FleetSnapshotEntry snapshotEntry : snapshot.fleet()) {
            GeoCoordinate coordinate = new GeoCoordinate(snapshotEntry.latitude(), snapshotEntry.longitude());
            MapPoint targetPoint = snapshotEntry.targetMapPointId() == null
                    ? null
                    : points.get(snapshotEntry.targetMapPointId());
            entries.add(new CourierLiveEntry(
                    courierFromSnapshot(snapshotEntry),
                    coordinate,
                    activeParcelFromSnapshot(snapshotEntry),
                    targetPoint,
                    snapshotEntry.phase(),
                    snapshotEntry.activeParcelCount()));
        }
        entries.sort(COURIER_LIST_ORDER);
        return entries;
    }

    private Courier courierFromSnapshot(FleetSnapshotEntry entry) {
        Courier courier = new Courier();
        courier.setId(entry.courierId());
        courier.setUserId(entry.courierUserId());
        courier.setFullName(entry.courierName());
        courier.setStatus(entry.courierStatus());
        courier.setVehicleNumber(entry.vehicleNumber());
        courier.setCurrentMapPointId(entry.currentMapPointId());
        courier.setCurrentLatitude(entry.latitude());
        courier.setCurrentLongitude(entry.longitude());
        courier.setCurrentPointName(entry.locationLabel());
        courier.setUpdatedAt(entry.updatedAt());
        return courier;
    }

    private Parcel activeParcelFromSnapshot(FleetSnapshotEntry entry) {
        if (entry.activeParcelId() == null) {
            return null;
        }
        Parcel parcel = new Parcel();
        parcel.setId(entry.activeParcelId());
        parcel.setStatus(entry.activeParcelStatus());
        parcel.setAssignedCourierId(entry.courierId());
        return parcel;
    }

    private void updateCourierUi(List<CourierLiveEntry> entries) {
        currentEntries = List.copyOf(entries);
        List<CourierLiveEntry> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort(COURIER_LIST_ORDER);

        List<Map<String, Object>> mapEntries = new ArrayList<>();
        for (CourierLiveEntry entry : sortedEntries) {
            Courier courier = entry.courier();
            mapEntries.add(Map.of(
                    "id", "courier-" + courier.getId(),
                    "lat", entry.coordinate().latitude(),
                    "lng", entry.coordinate().longitude(),
                    "label", courierLabel(courier),
                    "popup", courierPopup(entry),
                    "selected", selectedCourierId != null && selectedCourierId == courier.getId()
            ));
        }
        syncCourierMarkers(mapEntries);
        updateCourierListItems(sortedEntries);
        restoreSelection(sortedEntries);
        fleetStatus.setText("Widoczni kurierzy: " + sortedEntries.size());
    }

    private void updateCourierListItems(List<CourierLiveEntry> sortedEntries) {
        Double scrollValue = currentFleetScrollValue();
        ObservableList<CourierLiveEntry> items = courierList.getItems();
        if (items == null) {
            courierList.setItems(FXCollections.observableArrayList(sortedEntries));
            restoreFleetScrollValue(scrollValue);
            return;
        }
        items.setAll(sortedEntries);
        restoreFleetScrollValue(scrollValue);
    }

    private Double currentFleetScrollValue() {
        ScrollBar scrollBar = verticalFleetScrollBar();
        return scrollBar == null ? null : scrollBar.getValue();
    }

    private void restoreFleetScrollValue(Double value) {
        if (value == null) {
            return;
        }
        Platform.runLater(() -> Platform.runLater(() -> {
            ScrollBar scrollBar = verticalFleetScrollBar();
            if (scrollBar != null) {
                double min = scrollBar.getMin();
                double max = scrollBar.getMax();
                scrollBar.setValue(Math.max(min, Math.min(max, value)));
            }
        }));
    }

    private ScrollBar verticalFleetScrollBar() {
        courierList.applyCss();
        return courierList.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(scrollBar -> scrollBar.getOrientation() == Orientation.VERTICAL)
                .findFirst()
                .orElse(null);
    }

    private void syncCourierMarkers(List<Map<String, Object>> entries) {
        try {
            String json = ProtocolJson.mapper().writeValueAsString(entries);
            executeMapCommand("syncCouriers(" + json + ");");
        } catch (Exception e) {
            routeDetails.setText("Mapa chwilowo nie przyjęła listy kurierów.");
        }
    }

    private void restoreSelection(List<CourierLiveEntry> entries) {
        if (entries.isEmpty()) {
            courierList.getSelectionModel().clearSelection();
            selectedCourierId = null;
            clearRoutePreview();
            return;
        }
        CourierLiveEntry selected = null;
        if (selectedCourierId != null) {
            selected = entries.stream()
                    .filter(entry -> entry.courier().getId() == selectedCourierId)
                    .findFirst()
                    .orElse(null);
        }
        if (selected == null) {
            restoringSelection = true;
            try {
                courierList.getSelectionModel().clearSelection();
            } finally {
                restoringSelection = false;
            }
            clearRoutePreview(false);
            return;
        }
        CourierLiveEntry previousSelection = courierList.getSelectionModel().getSelectedItem();
        boolean sameSelectedCourier = previousSelection != null
                && previousSelection.courier().getId() == selected.courier().getId();
        restoringSelection = true;
        try {
            courierList.getSelectionModel().select(selected);
            if (!sameSelectedCourier && courierList.lookup(".scroll-bar") == null) {
                courierList.scrollTo(selected);
            }
        } finally {
            restoringSelection = false;
        }
        selectedCourierId = selected.courier().getId();
        showRouteForSelectedCourier(selected, false);
    }

    private void onMapReady() {
        if (disposed.get()) {
            return;
        }
        if (mapReady) {
            syncWarehouseMarkers();
            return;
        }
        mapReady = true;
        executeMapCommand(String.format(Locale.US, "applyThemeMode(%s);", jsValue(session.user().getTheme().name())));
        syncWarehouseMarkers();
        executeMapCommand(String.format(Locale.US, "setMapCenter(%.6f, %.6f, %d);",
                config.onlineMapDefaultCenterLat(),
                config.onlineMapDefaultCenterLng(),
                config.onlineMapDefaultZoom()));
        requestMapResize();
        flushPendingMapCommands();
    }

    private void onCourierSelected(CourierLiveEntry selected) {
        if (selected == null || disposed.get()) {
            return;
        }
        selectedCourierId = selected.courier().getId();
        if (!restoringSelection) {
            centerSelectedCourier(false);
            showRouteForSelectedCourier(selected, true);
        }
    }

    private void onMapCourierSelected(String markerId) {
        if (disposed.get() || markerId == null || !markerId.startsWith("courier-")) {
            return;
        }
        long courierId;
        try {
            courierId = Long.parseLong(markerId.substring("courier-".length()));
        } catch (NumberFormatException ignored) {
            return;
        }
        currentEntries.stream()
                .filter(entry -> entry.courier().getId() == courierId)
                .findFirst()
                .ifPresent(entry -> courierList.getSelectionModel().select(entry));
    }

    private void focusSelectedCourier() {
        centerSelectedCourier(true);
    }

    private void centerSelectedCourier(boolean follow) {
        CourierLiveEntry selected = selectedEntry();
        if (selected == null) {
            return;
        }
        executeMapCommand(String.format(Locale.US, "focusCourier(%s, %d, %s);",
                jsValue("courier-" + selected.courier().getId()), 16, follow ? "true" : "false"));
    }

    private void resetMapView() {
        executeMapCommand(String.format(Locale.US, "showOverview(%.6f, %.6f, %d);",
                config.onlineMapDefaultCenterLat(),
                config.onlineMapDefaultCenterLng(),
                config.onlineMapDefaultZoom()));
    }

    private void clearRoutePreview() {
        clearRoutePreview(true);
    }

    private void clearRoutePreview(boolean clearSelection) {
        routePreviewVersion.incrementAndGet();
        selectedCourierId = null;
        lastRoutePreviewKey = "";
        lastRouteScopeKey = "";
        lastRouteMetaText = "";
        lastRouteDetailsText = "";
        routeRequestInFlightKey = "";
        lastRouteGeometry = List.of();
        if (clearSelection) {
            restoringSelection = true;
            try {
                courierList.getSelectionModel().clearSelection();
            } finally {
                restoringSelection = false;
            }
        }
        executeMapCommand("clearRoute(); setSelectedCourier(null);");
        routeStatus.setText("Wybierz kuriera, aby zobaczyć jego bieżącą trasę.");
        routeMeta.setText("Tutaj pojawi się cel kuriera, dystans pozostały do przejazdu i przybliżony czas dotarcia.");
        routeDetails.setText("");
    }

    private void showRouteForSelectedCourier(CourierLiveEntry entry, boolean forceRouteRefresh) {
        String routeKey = routePreviewKey(entry);
        String routeScopeKey = routeScopeKey(entry);
        boolean samePreview = routeKey.equals(lastRoutePreviewKey);
        boolean sameRouteScope = routeScopeKey.equals(lastRouteScopeKey);
        String title = routeTitle(entry);
        String baseMeta = routeSummary(entry);
        String baseDetails = routeEtaPlaceholder(entry);
        if (!routeStatus.getText().equals(title)) {
            routeStatus.setText(title);
        }
        if (forceRouteRefresh || !samePreview || lastRouteMetaText.isBlank()) {
            setRouteMeta(baseMeta);
        }
        if (forceRouteRefresh || !sameRouteScope || lastRouteDetailsText.isBlank()) {
            setRouteDetails(baseDetails);
        }

        Long targetMapPointId = entry.targetPoint() == null ? null : entry.targetPoint().getId();
        if (RoutePreviewPolicy.shouldClearRoute(entry.phase(), targetMapPointId)) {
            clearRenderedRoute();
            if (entry.phase() != FleetTaskPhase.IDLE && entry.phase() != FleetTaskPhase.PICKUP_COOLDOWN) {
                routeDetails.setText("Brakuje współrzędnych celu dla wybranego kuriera.");
            }
            return;
        }
        boolean shouldFetchRoute = forceRouteRefresh
                || RoutePreviewPolicy.shouldFetchRoute(lastRoutePreviewKey, routeKey, lastRouteGeometry, entry.coordinate());
        if (!shouldFetchRoute) {
            executeMapCommand(String.format(Locale.US, "updateRouteStart(%.6f, %.6f);",
                    entry.coordinate().latitude(), entry.coordinate().longitude()));
            return;
        }
        if (!forceRouteRefresh && routeKey.equals(routeRequestInFlightKey)) {
            return;
        }
        lastRoutePreviewKey = routeKey;
        lastRouteScopeKey = routeScopeKey;
        routeRequestInFlightKey = routeKey;

        long requestId = routePreviewVersion.incrementAndGet();
        GeoCoordinate from = entry.coordinate();
        MapPoint target = entry.targetPoint();
        boolean shouldFitRoute = forceRouteRefresh || !sameRouteScope;
        CompletableFuture.supplyAsync(() -> osrmRouteService.fetchRoute(
                        from.latitude(), from.longitude(), target.getLatitude(), target.getLongitude()), executor)
                .whenComplete((route, error) -> Platform.runLater(() -> {
                    if (routeKey.equals(routeRequestInFlightKey)) {
                        routeRequestInFlightKey = "";
                    }
                    if (disposed.get() || routePreviewVersion.get() != requestId || selectedCourierId == null
                            || selectedCourierId != entry.courier().getId()) {
                        return;
                    }
                    if (error != null) {
                        onRouteError(errorText(error));
                        return;
                    }
                    renderRouteFromJava(route, shouldFitRoute, from, target);
                    setRouteMeta(routeSummary(entry));
                    setRouteDetails(routeTravelDetails(entry, route));
                }));
    }

    private void setRouteMeta(String text) {
        lastRouteMetaText = text == null ? "" : text;
        routeMeta.setText(lastRouteMetaText);
    }

    private void setRouteDetails(String text) {
        lastRouteDetailsText = text == null ? "" : text;
        routeDetails.setText(lastRouteDetailsText);
    }

    private void clearRenderedRoute() {
        routePreviewVersion.incrementAndGet();
        lastRoutePreviewKey = "";
        lastRouteScopeKey = "";
        routeRequestInFlightKey = "";
        lastRouteGeometry = List.of();
        executeMapCommand("clearRoute();");
    }

    private CourierLiveEntry selectedEntry() {
        CourierLiveEntry selected = courierList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected;
        }
        if (selectedCourierId == null) {
            return null;
        }
        return currentEntries.stream()
                .filter(entry -> entry.courier().getId() == selectedCourierId)
                .findFirst()
                .orElse(null);
    }

    private void renderRouteFromJava(OsrmRoute route, boolean fitRoute, GeoCoordinate from, MapPoint target) {
        try {
            List<List<Double>> latLngPairs = new ArrayList<>(route.geometry().stream()
                    .map(coordinate -> List.of(coordinate.latitude(), coordinate.longitude()))
                    .toList());
            if (!latLngPairs.isEmpty()) {
                if (from != null) {
                    latLngPairs.set(0, List.of(from.latitude(), from.longitude()));
                }
                if (target != null) {
                    latLngPairs.set(latLngPairs.size() - 1, List.of(target.getLatitude(), target.getLongitude()));
                }
            }
            lastRouteGeometry = latLngPairs.stream()
                    .map(pair -> new GeoCoordinate(pair.get(0), pair.get(1)))
                    .toList();
            String geometryJson = ProtocolJson.mapper().writeValueAsString(latLngPairs);
            executeMapCommand(String.format(Locale.US,
                    "renderRouteFromJava(%s, %.2f, %.2f, %s);",
                    geometryJson,
                    route.durationSeconds(),
                    route.distanceMeters(),
                    fitRoute ? "true" : "false"));
        } catch (Exception e) {
            routeDetails.setText("Mapa nie przyjęła pełnej geometrii trasy, ale ETA została przeliczona.");
        }
    }

    private void onRouteError(String message) {
        routeDetails.setText(message == null || message.isBlank()
                ? "Nie udało się pobrać trasy dla wybranego kuriera."
                : message);
    }

    private void executeMapCommand(String script) {
        if (disposed.get()) {
            return;
        }
        if (!mapReady) {
            if (pendingMapCommands.size() >= MAX_PENDING_MAP_COMMANDS) {
                pendingMapCommands.clear();
            }
            pendingMapCommands.add(script);
            return;
        }
        try {
            webEngine.executeScript(script);
        } catch (RuntimeException e) {
            routeDetails.setText("Mapa chwilowo nie przyjęła aktualizacji. Odśwież widok za moment.");
        }
    }

    private void requestMapResize() {
        if (disposed.get()) {
            return;
        }
        Runnable schedule = () -> {
            resizeDebounce.stop();
            resizeDebounce.setOnFinished(event -> executeMapCommand(String.format(Locale.US, "resizeMap(%.0f, %.0f);",
                    webView.getWidth(), webView.getHeight())));
            resizeDebounce.playFromStart();
        };
        if (Platform.isFxApplicationThread()) {
            schedule.run();
        } else {
            Platform.runLater(schedule);
        }
    }

    private void flushPendingMapCommands() {
        while (mapReady && !pendingMapCommands.isEmpty()) {
            String script = pendingMapCommands.poll();
            if (script != null) {
                try {
                    webEngine.executeScript(script);
                } catch (RuntimeException e) {
                    routeDetails.setText("Mapa chwilowo nie przyjęła aktualizacji. Odśwież widok za moment.");
                    break;
                }
            }
        }
    }

    private void syncWarehouseMarkers() {
        if (mapPointsById.isEmpty()) {
            return;
        }
        List<Map<String, Object>> warehouses = mapPointsById.values().stream()
                .filter(MapPoint::isWarehouse)
                .map(point -> Map.<String, Object>of(
                        "id", "warehouse-" + point.getId(),
                        "lat", point.getLatitude(),
                        "lng", point.getLongitude(),
                        "label", point.getName(),
                        "popup", warehousePopup(point)
                ))
                .toList();
        try {
            String json = ProtocolJson.mapper().writeValueAsString(warehouses);
            executeMapCommand("syncWarehouses(" + json + ");");
        } catch (Exception e) {
            routeDetails.setText("Mapa chwilowo nie przyjęła listy punktów centrali.");
        }
    }

    private HBox actionRow(javafx.scene.Node... nodes) {
        HBox row = new HBox(8, nodes);
        row.setAlignment(Pos.CENTER_LEFT);
        for (javafx.scene.Node node : nodes) {
            if (node instanceof Region region) {
                region.setMinWidth(0);
                region.setPrefWidth(0);
                region.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(region, Priority.ALWAYS);
            }
        }
        return row;
    }

    private Label subsection(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("subsection-title");
        return label;
    }

    private String routeTitle(CourierLiveEntry entry) {
        return switch (entry.phase()) {
            case TO_PICKUP -> entry.courier().getFullName() + " jedzie do nadawcy";
            case PICKUP_COOLDOWN -> entry.courier().getFullName() + " odbiera paczkę u nadawcy";
            case TO_DELIVERY -> entry.courier().getFullName() + " jedzie do odbiorcy";
            case RETURN_TO_CENTRAL -> entry.courier().getFullName() + " wraca do Centrali";
            case IDLE -> entry.courier().getFullName() + " oczekuje na zlecenie";
        };
    }

    private String routeSummary(CourierLiveEntry entry) {
        if (entry.phase() == FleetTaskPhase.RETURN_TO_CENTRAL && entry.targetPoint() != null) {
            return "Cel: " + entry.targetPoint().getName();
        }
        if (entry.activeParcel() == null || entry.targetPoint() == null) {
            return "Brak aktywnej paczki dla wybranego kuriera.";
        }
        return "Paczka #" + entry.activeParcel().getId()
                + " | Cel: " + entry.targetPoint().getName();
    }

    private String routeEtaPlaceholder(CourierLiveEntry entry) {
        if (entry.phase() == FleetTaskPhase.RETURN_TO_CENTRAL) {
            return "ETA: wyznaczanie trasy do Centrali";
        }
        if (entry.activeParcel() == null) {
            return "";
        }
        if (entry.phase() == FleetTaskPhase.PICKUP_COOLDOWN) {
            return "ETA: po zakończeniu odbioru";
        }
        return "ETA: wyznaczanie trasy";
    }

    private String routeTravelDetails(CourierLiveEntry entry, OsrmRoute route) {
        String stage = switch (entry.phase()) {
            case TO_PICKUP -> "do nadawcy";
            case TO_DELIVERY -> "do odbiorcy";
            case RETURN_TO_CENTRAL -> "do Centrali";
            case PICKUP_COOLDOWN -> "odbiór";
            case IDLE -> "postój";
        };
        String distance = String.format(Locale.forLanguageTag("pl-PL"), "%.1f km", route.distanceMeters() / 1000.0);
        return "ETA: " + formatDuration(route.durationSeconds())
                + " | Dystans: " + distance
                + " | Etap: " + stage;
    }

    private String routePreviewKey(CourierLiveEntry entry) {
        return routeScopeKey(entry);
    }

    private String routeScopeKey(CourierLiveEntry entry) {
        long parcelId = entry.activeParcel() == null ? 0 : entry.activeParcel().getId();
        long targetId = entry.targetPoint() == null ? 0 : entry.targetPoint().getId();
        return entry.courier().getId() + "|" + parcelId + "|" + targetId + "|" + entry.phase();
    }

    private static int parcelAssignmentGroup(CourierLiveEntry entry) {
        return entry.activeParcel() == null ? 1 : 0;
    }

    private static int parcelDeliveryProgress(CourierLiveEntry entry) {
        Parcel parcel = entry.activeParcel();
        if (parcel == null) {
            return Integer.MAX_VALUE;
        }
        return switch (parcel.getStatus()) {
            case OUT_FOR_DELIVERY -> 0;
            case IN_TRANSIT, WAREHOUSE -> 1;
            case PICKUP_IN_PROGRESS -> 2;
            case WAITING_FOR_COURIER -> 3;
            default -> 4;
        };
    }

    private String courierLabel(Courier courier) {
        return courier.getFullName() + " (" + courier.getStatus().displayName() + ")";
    }

    private String courierPopup(CourierLiveEntry entry) {
        Courier courier = entry.courier();
        String vehicle = courier.getVehicleNumber() == null ? "" : courier.getVehicleNumber();
        String current = courier.getCurrentPointName() == null ? "Brak lokalizacji" : courier.getCurrentPointName();
        String target = entry.targetPoint() == null ? "" : entry.targetPoint().getName();
        StringBuilder builder = new StringBuilder();
        builder.append("<div class=\"courier-popup\">")
                .append("<strong>").append(escapeHtml(courier.getFullName()))
                .append(" • ").append(escapeHtml(courier.getStatus().displayName())).append("</strong>")
                .append("<br>Lokalizacja: ").append(formatPopupLocation(current));
        if (!target.isBlank() && !normalizeSearch(current).contains(normalizeSearch(target))) {
            builder.append("<br>Cel: ").append(escapeHtml(target));
        }
        if (!vehicle.isBlank()) {
            builder.append("<br>Pojazd: ").append(escapeHtml(vehicle));
        }
        builder.append("</div>");
        return builder.toString();
    }

    private String warehousePopup(MapPoint point) {
        String address = "Centrala".equals(point.getName())
                ? "Warszawska 24, 31-155 Kraków"
                : "Punkt centralny DeliverFlow";
        return "<strong>" + point.getName() + "</strong><br>" + address;
    }

    private String formatPopupLocation(String location) {
        return escapeHtml(location)
                .replace(": ul.", ":<br>ul.")
                .replace(": al.", ":<br>al.")
                .replace(": Aleja", ":<br>Aleja")
                .replace(": os.", ":<br>os.");
    }

    private String formatDuration(double seconds) {
        long roundedMinutes = Math.round(seconds / 60.0);
        long hours = roundedMinutes / 60;
        long minutes = roundedMinutes % 60;
        if (hours == 0) {
            return minutes + " min";
        }
        return hours + " godz. " + minutes + " min";
    }

    private String jsValue(String value) {
        try {
            return ProtocolJson.mapper().writeValueAsString(value);
        } catch (Exception e) {
            return "\"\"";
        }
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String normalizeSearch(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private String errorText(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Wystąpił błąd." : current.getMessage();
    }

    private ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "deliverflow-online-map");
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        refreshTimeline.stop();
        resizeDebounce.stop();
        executor.shutdownNow();
        pendingMapCommands.clear();
        refreshInProgress.set(false);
    }

    private record DataSnapshot(List<FleetSnapshotEntry> fleet, List<MapPoint> points) {
    }

    private record CourierLiveEntry(Courier courier, GeoCoordinate coordinate, Parcel activeParcel,
                                    MapPoint targetPoint, FleetTaskPhase phase, int activeParcelCount) {
    }

    private static final class CourierLiveCell extends ListCell<CourierLiveEntry> {
        private final long currentUserId;
        private final Label title = new Label();
        private final Label subtitle = new Label();
        private final Label meta = new Label();
        private final VBox content = new VBox(4);

        private CourierLiveCell(long currentUserId) {
            this.currentUserId = currentUserId;
            title.getStyleClass().add("courier-live-title");
            subtitle.getStyleClass().add("courier-live-subtitle");
            meta.getStyleClass().add("courier-live-meta");
            title.setWrapText(true);
            subtitle.setWrapText(true);
            meta.setWrapText(true);
            title.setMinHeight(Region.USE_PREF_SIZE);
            subtitle.setMinHeight(Region.USE_PREF_SIZE);
            meta.setMinHeight(Region.USE_PREF_SIZE);
            title.setTextOverrun(OverrunStyle.CLIP);
            subtitle.setTextOverrun(OverrunStyle.CLIP);
            meta.setTextOverrun(OverrunStyle.CLIP);
            content.setMaxWidth(Double.MAX_VALUE);
            content.getStyleClass().add("courier-live-cell");
            content.getChildren().addAll(title, subtitle, meta);
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(CourierLiveEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Courier courier = item.courier();
            String you = courier.getUserId() == currentUserId ? "Ty • " : "";
            title.setText(you + courier.getFullName() + " • " + courier.getStatus().displayName());
            subtitle.setText(routeLine(item));
            String updated = courier.getUpdatedAt() == null ? "brak czasu" : TimeUtil.formatDisplay(courier.getUpdatedAt());
            String parcelInfo = parcelInfo(item);
            meta.setText(parcelInfo + " • " + updated);
            setText(null);
            setGraphic(content);
        }

        private String parcelInfo(CourierLiveEntry item) {
            if (item.phase() == FleetTaskPhase.RETURN_TO_CENTRAL) {
                return "Powrót do Centrali";
            }
            if (item.activeParcel() == null) {
                return "Dostępny bez aktywnej paczki";
            }
            return "Paczka #" + item.activeParcel().getId() + " • " + item.activeParcel().getStatus().displayName();
        }

        private String routeLine(CourierLiveEntry item) {
            String target = item.targetPoint() == null ? "" : item.targetPoint().getName();
            return switch (item.phase()) {
                case TO_PICKUP -> "W drodze do odbioru: " + nullToDash(target);
                case PICKUP_COOLDOWN -> "Odbiór paczki u nadawcy: " + nullToDash(item.courier().getCurrentPointName());
                case TO_DELIVERY -> "W drodze do dostawy: " + nullToDash(target);
                case RETURN_TO_CENTRAL -> "Wraca do Centrali: " + nullToDash(target);
                case IDLE -> nullToDash(item.courier().getCurrentPointName());
            };
        }

        private String nullToDash(String value) {
            return value == null || value.isBlank() ? "Brak lokalizacji" : value;
        }
    }
}
