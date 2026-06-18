package client.map;

import javafx.application.Platform;

import java.util.Objects;
import java.util.function.Consumer;

public class OnlineMapJavaConnector {
    private final Runnable onMapReady;
    private final Consumer<String> onCourierSelected;

    public OnlineMapJavaConnector(Runnable onMapReady,
                                  Consumer<String> onCourierSelected) {
        this.onMapReady = Objects.requireNonNullElse(onMapReady, () -> {
        });
        this.onCourierSelected = Objects.requireNonNullElse(onCourierSelected, ignored -> {
        });
    }

    public void mapReady() {
        Platform.runLater(onMapReady);
    }

    public void selectCourier(String markerId) {
        Platform.runLater(() -> onCourierSelected.accept(markerId));
    }
}
