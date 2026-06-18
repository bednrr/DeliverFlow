package model;

public enum CourierStatus {
    AVAILABLE("Dostępny"),
    BUSY("Zajęty"),
    BREAK("Przerwa"),
    OFFLINE("Offline");

    private final String displayName;

    CourierStatus(String displayName) {
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
