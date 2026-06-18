package model;

public class MapPoint {
    private long id;
    private String name;
    private double latitude;
    private double longitude;
    private boolean warehouse;

    public MapPoint() {
    }

    public MapPoint(long id, String name, double latitude, double longitude, boolean warehouse) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.warehouse = warehouse;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public boolean isWarehouse() {
        return warehouse;
    }

    public void setWarehouse(boolean warehouse) {
        this.warehouse = warehouse;
    }

    @Override
    public String toString() {
        return name;
    }
}
