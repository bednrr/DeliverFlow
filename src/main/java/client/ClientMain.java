package client;

import client.controller.SessionContext;
import client.network.ApiClient;
import client.view.DashboardView;
import client.view.LoginView;
import client.view.LogoAssets;
import client.view.UiDialogs;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import model.Theme;
import util.AppConfig;

public class ClientMain extends Application {
    private SessionContext session;
    private Scene scene;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        Font.loadFont(getClass().getResourceAsStream("/fonts/Inter-Regular.ttf"), 14);
        Font.loadFont(getClass().getResourceAsStream("/fonts/Inter-SemiBold.ttf"), 14);
        Font.loadFont(getClass().getResourceAsStream("/fonts/Inter-Bold.ttf"), 14);
        ApiClient apiClient = new ApiClient(new AppConfig());
        session = new SessionContext(apiClient);
        LoginView loginView = new LoginView(session, user -> showDashboard(stage));
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1320, Math.max(980, screen.getWidth() * 0.92));
        double height = Math.min(900, Math.max(640, screen.getHeight() * 0.88));
        scene = new Scene(loginView.root(), width, height);
        applyTheme(Theme.LIGHT);
        stage.setTitle("DeliverFlow");
        if (LogoAssets.appIconImage() != null) {
            stage.getIcons().add(LogoAssets.appIconImage());
        }
        stage.setScene(scene);
        stage.setMinWidth(Math.min(980, screen.getWidth() * 0.88));
        stage.setMinHeight(Math.min(640, screen.getHeight() * 0.84));
        stage.show();
    }

    private void showDashboard(Stage stage) {
        DashboardView dashboard = new DashboardView(session, () -> showLogin(stage), this::applyTheme);
        scene.setRoot(dashboard.root());
        applyTheme(session.user().getTheme());
    }

    private void showLogin(Stage stage) {
        LoginView loginView = new LoginView(session, user -> showDashboard(stage));
        scene.setRoot(loginView.root());
        applyTheme(Theme.LIGHT);
    }

    private void applyTheme(Theme theme) {
        scene.getStylesheets().clear();
        String stylesheet = session.apiClient().cssFor(theme);
        scene.getStylesheets().add(stylesheet);
        UiDialogs.configure(stylesheet, LogoAssets.appIconImage(), primaryStage);
    }

    @Override
    public void stop() {
        if (session != null) {
            session.apiClient().shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
