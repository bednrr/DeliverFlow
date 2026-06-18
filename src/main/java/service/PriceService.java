package service;

import model.ParcelSize;
import model.RouteResult;
import util.PricingFormula;

import java.math.BigDecimal;

public class PriceService {
    private final MapService mapService;

    public PriceService(MapService mapService) {
        this.mapService = mapService;
    }

    public BigDecimal estimate(ParcelSize size, double weightKg, long senderPointId, long receiverPointId) {
        RouteResult route = mapService.shortestRoute(senderPointId, receiverPointId);
        return PricingFormula.calculate(size, weightKg, safeMinutes(route));
    }

    private int safeMinutes(RouteResult route) {
        return route.exists() ? route.getMinutes() : 60;
    }
}
