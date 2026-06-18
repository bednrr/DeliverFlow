package model;

public enum Theme {
    LIGHT("Jasny"),
    DARK("Ciemny");

    private final String displayName;

    Theme(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
