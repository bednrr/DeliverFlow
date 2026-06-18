package client.view;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Arrays;
import java.util.List;

public final class UiDialogs {
    private static final double MESSAGE_BOX_WIDTH = 840;
    private static final double DIALOG_SIDE_PADDING = 30;
    private static final double SCREEN_MARGIN = 72;
    private static final double MIN_DIALOG_WIDTH = 420;
    private static final double MIN_DIALOG_HEIGHT = 320;
    private static String stylesheet;
    private static Image windowIcon;
    private static Window ownerWindow;

    private UiDialogs() {
    }

    public static void configure(String stylesheet, Image windowIcon) {
        configure(stylesheet, windowIcon, ownerWindow);
    }

    public static void configure(String stylesheet, Image windowIcon, Window ownerWindow) {
        UiDialogs.stylesheet = stylesheet;
        UiDialogs.windowIcon = windowIcon;
        UiDialogs.ownerWindow = ownerWindow;
    }

    public static void info(String message) {
        info("Operacja zakończona powodzeniem", message);
    }

    public static void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("DeliverFlow");
        alert.setHeaderText(title);
        alert.getDialogPane().setContent(wrapped(message));
        prepare(alert);
        alert.showAndWait();
    }

    public static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("DeliverFlow");
        alert.setHeaderText("Nie udało się wykonać operacji");
        alert.getDialogPane().setContent(wrapped(message));
        prepare(alert);
        alert.showAndWait();
    }

    public static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("DeliverFlow");
        alert.setHeaderText("Potwierdź operację");
        alert.getDialogPane().setContent(wrapped(message));
        ButtonType yes = new ButtonType("Tak", ButtonType.OK.getButtonData());
        ButtonType cancel = new ButtonType("Anuluj", ButtonType.CANCEL.getButtonData());
        alert.getButtonTypes().setAll(yes, cancel);
        prepare(alert);
        return alert.showAndWait().orElse(cancel) == yes;
    }

    public static void text(String title, String content) {
        TextArea area = new TextArea(content);
        area.setEditable(false);
        area.setWrapText(true);
        area.setMinWidth(MESSAGE_BOX_WIDTH);
        area.setPrefSize(MESSAGE_BOX_WIDTH, 660);
        area.setMaxWidth(MESSAGE_BOX_WIDTH);
        area.getStyleClass().add("dialog-text-area");
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("DeliverFlow");
        alert.setHeaderText(title);
        alert.getDialogPane().setContent(dialogContent(area, MESSAGE_BOX_WIDTH));
        prepare(alert);
        alert.showAndWait();
    }

    public static void custom(String title, Node content, double width, double height, String styleClass) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("DeliverFlow");
        alert.setHeaderText(null);
        double dialogWidth = fitDialogWidth(width + DIALOG_SIDE_PADDING * 2);
        double contentWidth = Math.max(320, dialogWidth - DIALOG_SIDE_PADDING * 2);
        double dialogHeight = fitDialogHeight(height);
        alert.getDialogPane().setContent(dialogContent(content, contentWidth));
        prepare(alert);
        if (styleClass != null && !styleClass.isBlank()) {
            alert.getDialogPane().getStyleClass().add(styleClass);
        }
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(dialogWidth, dialogHeight);
        alert.getDialogPane().setMinWidth(Math.min(dialogWidth, 760));
        alert.showAndWait();
    }

    private static void prepare(Alert alert) {
        alert.setGraphic(null);
        DialogPane pane = alert.getDialogPane();
        pane.getStyleClass().add("app-dialog");
        pane.getStyleClass().add(dialogClass(alert.getAlertType()));
        pane.setMinWidth(Math.min(760, fitDialogWidth(760)));
        pane.setPrefWidth(fitDialogWidth(900));
        if (stylesheet != null && !pane.getStylesheets().contains(stylesheet)) {
            pane.getStylesheets().add(stylesheet);
        }
        Window owner = activeOwner();
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setOnShown(event -> {
            if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
                if (windowIcon != null) {
                    stage.getIcons().setAll(windowIcon);
                }
                keepOnScreen(stage);
            }
        });
    }

    private static String dialogClass(Alert.AlertType type) {
        if (type == Alert.AlertType.ERROR) {
            return "dialog-error";
        }
        if (type == Alert.AlertType.CONFIRMATION) {
            return "dialog-confirm";
        }
        return "dialog-info";
    }

    private static Node wrapped(String message) {
        return dialogContent(messageList(splitMessage(message)), MESSAGE_BOX_WIDTH);
    }

    private static List<String> splitMessage(String message) {
        String normalized = message == null || message.isBlank() ? "Brak treści komunikatu." : message.strip();
        return Arrays.stream(normalized.split("\\R+"))
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static VBox messageList(List<String> blocks) {
        VBox list = new VBox(8);
        list.getStyleClass().add("dialog-message-list");
        list.setFillWidth(true);
        list.setMinWidth(MESSAGE_BOX_WIDTH);
        list.setPrefWidth(MESSAGE_BOX_WIDTH);
        list.setMaxWidth(MESSAGE_BOX_WIDTH);
        List<String> safeBlocks = blocks == null || blocks.isEmpty()
                ? List.of("Brak treści komunikatu.")
                : blocks;
        for (String safeBlock : safeBlocks) {
            list.getChildren().add(messageItem(safeBlock));
        }
        return list;
    }

    private static StackPane dialogContent(Node content, double width) {
        if (content instanceof Region region) {
            region.setMinWidth(0);
            region.setPrefWidth(width);
            region.setMaxWidth(Double.MAX_VALUE);
        }
        StackPane wrapper = new StackPane(content);
        wrapper.getStyleClass().add("dialog-content-wrapper");
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(14, DIALOG_SIDE_PADDING, 0, DIALOG_SIDE_PADDING));
        wrapper.setMinWidth(0);
        wrapper.setPrefWidth(width + DIALOG_SIDE_PADDING * 2);
        wrapper.setMaxWidth(Double.MAX_VALUE);
        return wrapper;
    }

    private static double fitDialogWidth(double desiredWidth) {
        Rectangle2D bounds = preferredBounds();
        double maxWidth = Math.max(320, bounds.getWidth() - SCREEN_MARGIN);
        return Math.max(Math.min(MIN_DIALOG_WIDTH, maxWidth), Math.min(desiredWidth, maxWidth));
    }

    private static double fitDialogHeight(double desiredHeight) {
        Rectangle2D bounds = preferredBounds();
        double maxHeight = Math.max(260, bounds.getHeight() - SCREEN_MARGIN);
        return Math.max(Math.min(MIN_DIALOG_HEIGHT, maxHeight), Math.min(desiredHeight, maxHeight));
    }

    private static void keepOnScreen(Stage stage) {
        Window owner = stage.getOwner() == null ? activeOwner() : stage.getOwner();
        Rectangle2D bounds = boundsFor(owner == null ? stage : owner);
        double maxWidth = Math.max(320, bounds.getWidth() - SCREEN_MARGIN);
        double maxHeight = Math.max(260, bounds.getHeight() - SCREEN_MARGIN);
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

    private static Window activeOwner() {
        return ownerWindow != null && ownerWindow.isShowing() ? ownerWindow : null;
    }

    private static Rectangle2D preferredBounds() {
        Window owner = activeOwner();
        return owner == null ? Screen.getPrimary().getVisualBounds() : boundsFor(owner);
    }

    private static Rectangle2D boundsFor(Window window) {
        return Screen.getScreensForRectangle(window.getX(), window.getY(), window.getWidth(), window.getHeight())
                .stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static StackPane messageItem(String message) {
        Node content = plainMessage(message);
        StackPane box = new StackPane(content);
        box.getStyleClass().add("dialog-message-box");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static Label plainMessage(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("dialog-message");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

}
