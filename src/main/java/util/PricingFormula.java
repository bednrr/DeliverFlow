package util;

import model.ParcelSize;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PricingFormula {
    private PricingFormula() {
    }

    public static BigDecimal calculate(ParcelSize size, double weightKg, double routeMinutes) {
        if (size == null) {
            throw new IllegalArgumentException("Parcel size is required.");
        }
        double normalizedWeight = Math.max(0, weightKg);
        double normalizedRouteMinutes = Math.max(0, routeMinutes);
        double base = 12.0;
        double distancePart = normalizedRouteMinutes * 0.55;
        double weightPart = normalizedWeight * 1.2;
        double value = (base + distancePart + weightPart) * size.priceMultiplier();
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
