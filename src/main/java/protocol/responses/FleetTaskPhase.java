package protocol.responses;

public enum FleetTaskPhase {
    IDLE(3),
    TO_PICKUP(2),
    PICKUP_COOLDOWN(1),
    RETURN_TO_CENTRAL(1),
    TO_DELIVERY(0);

    private final int priority;

    FleetTaskPhase(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
