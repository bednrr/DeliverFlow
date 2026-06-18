package model;

public enum UserRole {
    CLIENT("Klient"),
    COURIER("Kurier"),
    DISPATCHER("Dyspozytor"),
    ADMIN("Administrator");

    private final String displayName;

    UserRole(String displayName) {
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
