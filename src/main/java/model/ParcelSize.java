package model;

public enum ParcelSize {
    SMALL("Mała", "30 x 20 x 10 cm", 1.0),
    MEDIUM("Średnia", "50 x 35 x 25 cm", 1.35),
    LARGE("Duża", "80 x 50 x 40 cm", 1.8);

    private final String displayName;
    private final String dimensions;
    private final double priceMultiplier;

    ParcelSize(String displayName, String dimensions, double priceMultiplier) {
        this.displayName = displayName;
        this.dimensions = dimensions;
        this.priceMultiplier = priceMultiplier;
    }

    public String displayName() {
        return displayName;
    }

    public String dimensions() {
        return dimensions;
    }

    public String displayWithDimensions() {
        return displayName + " (" + dimensions + ")";
    }

    public double priceMultiplier() {
        return priceMultiplier;
    }

    @Override
    public String toString() {
        return displayWithDimensions();
    }
}
