package client.view;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.net.URL;

public final class LogoAssets {
    private static final String APP_ICON_PATH = "/logo-icon.png";
    private static final String FULL_LOGO_PATH = "/logo-long-transparent.png";

    private LogoAssets() {
    }

    public static Image appIconImage() {
        return loadImage(APP_ICON_PATH);
    }

    public static Image fullLogoImage() {
        return loadImage(FULL_LOGO_PATH);
    }

    private static Image loadImage(String path) {
        URL resource = LogoAssets.class.getResource(path);
        if (resource == null) {
            return null;
        }
        return new Image(resource.toExternalForm());
    }

    public static StackPane loginLogo() {
        return logoHolder(fullLogoImage(), 520, 120);
    }

    public static StackPane compactLoginLogo() {
        return logoHolder(fullLogoImage(), 440, 96);
    }

    public static StackPane sidebarLogo() {
        return logoHolder(fullLogoImage(), 212, 66);
    }

    private static StackPane logoHolder(Image image, double width, double height) {
        StackPane holder = new StackPane();
        holder.getStyleClass().add("logo-holder");
        holder.setPrefSize(width, height);
        holder.setMinSize(width, height);
        holder.setMaxSize(width, height);
        if (image == null) {
            return holder;
        }
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        holder.getChildren().add(imageView);
        return holder;
    }
}
