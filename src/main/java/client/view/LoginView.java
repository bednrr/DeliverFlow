package client.view;

import client.controller.SessionContext;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import model.User;
import protocol.MessageType;
import protocol.ProtocolJson;
import protocol.requests.LoginRequest;
import protocol.requests.RegisterClientRequest;
import protocol.responses.LoginResponse;
import util.PhoneUtil;

import java.util.function.Consumer;

public class LoginView {
    private final SessionContext session;
    private final Consumer<User> onLogin;
    private final BorderPane root = new BorderPane();
    private final Label message = new Label();
    private boolean registerMode;

    public LoginView(SessionContext session, Consumer<User> onLogin) {
        this.session = session;
        this.onLogin = onLogin;
        render();
    }

    public Parent root() {
        return root;
    }

    private void render() {
        root.setPadding(Insets.EMPTY);
        VBox box = new VBox(20);
        box.setMaxWidth(520);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("panel");
        box.getStyleClass().add("login-panel");
        if (registerMode) {
            box.getStyleClass().add("login-panel-compact");
        }
        box.setPadding(registerMode ? new Insets(24, 40, 24, 40) : new Insets(36, 40, 36, 40));

        Label subtitle = new Label(registerMode ? "Utwórz konto klienta" : "Logowanie do systemu");
        subtitle.getStyleClass().add("login-subtitle");
        subtitle.setMaxWidth(Double.MAX_VALUE);
        subtitle.setAlignment(Pos.CENTER);
        message.getStyleClass().add("muted");
        message.setMaxWidth(Double.MAX_VALUE);
        message.setAlignment(Pos.CENTER);

        TextField firstName = new TextField();
        firstName.setPromptText("Imię");
        TextField lastName = new TextField();
        lastName.setPromptText("Nazwisko");
        TextField email = new TextField();
        email.setPromptText("Adres e-mail");
        TextField phone = new TextField();
        phone.setPromptText("Numer telefonu (9 cyfr)");
        PasswordField password = new PasswordField();
        password.setPromptText("Hasło");
        PasswordField repeat = new PasswordField();
        repeat.setPromptText("Powtórz hasło");
        BooleanProperty showPassword = new SimpleBooleanProperty(false);
        PasswordFieldView.PasswordControl passwordControl = PasswordFieldView.wrap(password, showPassword);
        PasswordFieldView.PasswordControl repeatControl = PasswordFieldView.wrap(repeat, showPassword);

        VBox fields = new VBox(registerMode ? 9 : 12);
        fields.setMaxWidth(Double.MAX_VALUE);
        if (registerMode) {
            fields.getChildren().addAll(
                    fieldWithLabel("Imię", firstName),
                    fieldWithLabel("Nazwisko", lastName));
        }
        fields.getChildren().add(fieldWithLabel("Adres e-mail", email));
        if (registerMode) {
            fields.getChildren().add(fieldWithLabel("Telefon", phone));
        }
        fields.getChildren().add(fieldWithLabel("Hasło", passwordControl.node()));
        if (registerMode) {
            fields.getChildren().add(fieldWithLabel("Powtórz hasło", repeatControl.node()));
        }
        fields.getChildren().add(PasswordFieldView.toggle(showPassword));

        Button submit = new Button(registerMode ? "Zarejestruj" : "Zaloguj");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        submit.setMinHeight(48);
        Hyperlink switchMode = new Hyperlink(registerMode ? "Mam już konto" : "Utwórz konto klienta");
        switchMode.getStyleClass().add("login-switch");
        switchMode.setOnAction(event -> {
            registerMode = !registerMode;
            render();
        });
        HBox switchRow = new HBox(switchMode);
        switchRow.setAlignment(Pos.CENTER);
        switchRow.setMaxWidth(Double.MAX_VALUE);
        submit.setOnAction(event -> {
            submit.setDisable(true);
            if (registerMode) {
                register(firstName.getText(), lastName.getText(), email.getText(), phone.getText(),
                        password.getText(), repeat.getText(), submit);
            } else {
                login(email.getText(), password.getText(), submit);
            }
        });
        firstName.setOnAction(event -> submit.fire());
        lastName.setOnAction(event -> submit.fire());
        email.setOnAction(event -> submit.fire());
        phone.setOnAction(event -> submit.fire());
        password.setOnAction(event -> submit.fire());
        repeat.setOnAction(event -> submit.fire());
        passwordControl.visibleField().setOnAction(event -> submit.fire());
        repeatControl.visibleField().setOnAction(event -> submit.fire());
        Node loginLogo = registerMode ? LogoAssets.compactLoginLogo() : LogoAssets.loginLogo();
        loginLogo.getStyleClass().add("login-logo-holder");
        box.getChildren().setAll(loginLogo, subtitle, fields, submit, switchRow, message);
        StackPane scrollContent = new StackPane(box);
        scrollContent.setAlignment(Pos.CENTER);
        scrollContent.setPadding(new Insets(40));

        ScrollPane scrollPane = new ScrollPane(scrollContent);
        scrollPane.getStyleClass().add("auth-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        root.setCenter(scrollPane);
        BorderPane.setAlignment(scrollPane, Pos.CENTER);
    }

    private VBox fieldWithLabel(String labelText, Node field) {
        Label label = new Label(labelText);
        label.getStyleClass().add("muted");
        if (field instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        VBox box = new VBox(4, label, field);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void login(String email, String password, Button submit) {
        session.apiClient().send(MessageType.LOGIN_REQUEST, new LoginRequest(email, password))
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    submit.setDisable(false);
                    if (error != null) {
                        message.setText(error.getCause() == null ? error.getMessage() : error.getCause().getMessage());
                        return;
                    }
                    LoginResponse loginResponse = ProtocolJson.fromJson(response.getPayload(), LoginResponse.class);
                    session.login(loginResponse.token(), loginResponse.user());
                    onLogin.accept(loginResponse.user());
                }));
    }

    private void register(String firstName, String lastName, String email, String phone,
                          String password, String repeatPassword, Button submit) {
        if (isBlank(firstName) || isBlank(lastName) || isBlank(email)
                || isBlank(phone) || isBlank(password) || isBlank(repeatPassword)) {
            submit.setDisable(false);
            message.setText("Uzupełnij wszystkie dane rejestracji.");
            return;
        }
        if (!PhoneUtil.isValid(phone)) {
            submit.setDisable(false);
            message.setText("Numer telefonu musi mieć 9 cyfr.");
            return;
        }
        RegisterClientRequest request = new RegisterClientRequest(firstName, lastName, email, PhoneUtil.normalize(phone), password, repeatPassword);
        session.apiClient().send(MessageType.REGISTER_CLIENT_REQUEST, request)
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    submit.setDisable(false);
                    if (error != null) {
                        message.setText(error.getCause() == null ? error.getMessage() : error.getCause().getMessage());
                        return;
                    }
                    message.setText("Konto utworzone. Możesz się zalogować.");
                    registerMode = false;
                    render();
                }));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
