package client.view;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class PasswordFieldView {
    private PasswordFieldView() {
    }

    public static PasswordControl wrap(PasswordField passwordField, BooleanProperty showPassword) {
        TextField visibleField = new TextField();
        visibleField.textProperty().bindBidirectional(passwordField.textProperty());
        visibleField.visibleProperty().bind(showPassword);
        passwordField.visibleProperty().bind(showPassword.not());
        passwordField.setMaxWidth(Double.MAX_VALUE);
        visibleField.setMaxWidth(Double.MAX_VALUE);

        StackPane stack = new StackPane(passwordField, visibleField);
        stack.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(stack);
        box.setMaxWidth(Double.MAX_VALUE);
        return new PasswordControl(box, visibleField);
    }

    public static CheckBox toggle(BooleanProperty showPassword) {
        CheckBox checkBox = new CheckBox("Pokaż hasło");
        checkBox.selectedProperty().bindBidirectional(showPassword);
        checkBox.getStyleClass().add("password-toggle");
        return checkBox;
    }

    public record PasswordControl(VBox node, TextField visibleField) {
    }
}
