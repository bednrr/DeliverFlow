package model;

import java.util.ArrayList;
import java.util.List;

public class RouteResult {
    private final List<MapPoint> points;
    private final int minutes;
    private final double distanceKm;

    public RouteResult(List<MapPoint> points, int minutes, double distanceKm) {
        this.points = new ArrayList<>(points);
        this.minutes = minutes;
        this.distanceKm = distanceKm;
    }

    public int getMinutes() {
        return minutes;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public boolean exists() {
        return !points.isEmpty();
    }

}
