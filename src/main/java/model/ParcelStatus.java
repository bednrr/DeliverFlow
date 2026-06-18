package model;

public enum ParcelStatus {
    WAITING_FOR_COURIER("Oczekuje na kuriera"),
    PICKUP_IN_PROGRESS("Odbiór paczki u nadawcy"),
    IN_TRANSIT("W drodze"),
    WAREHOUSE("W centrali"),
    OUT_FOR_DELIVERY("Wydana do doręczenia"),
    DELIVERED("Doręczona"),
    CANCELED("Anulowana"),
    DELIVERY_PROBLEM("Problem z doręczeniem");

    private final String displayName;

    ParcelStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ParcelStatus fromDatabase(String value) {
        if ("CREATED".equals(value) || "COURIER_TO_SENDER".equals(value)) {
            return WAITING_FOR_COURIER;
        }
        if (("TO_" + "WAREHOUSE").equals(value)) {
            return IN_TRANSIT;
        }
        if ("PICKED_UP".equals(value)) {
            return PICKUP_IN_PROGRESS;
        }
        if (("IN_" + "WAREHOUSE").equals(value)) {
            return PICKUP_IN_PROGRESS;
        }
        return ParcelStatus.valueOf(value);
    }

    public boolean canBeCanceled() {
        return this == WAITING_FOR_COURIER;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
