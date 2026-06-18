package model;

import java.time.LocalDateTime;

public class Courier {
    private long id;
    private long userId;
    private CourierStatus status;
    private long currentMapPointId;
    private String vehicleNumber;
    private LocalDateTime updatedAt;
    private String fullName;
    private String email;
    private String phone;
    private String currentPointName;
    private Double currentLatitude;
    private Double currentLongitude;
    private String shiftStart;
    private String shiftEnd;
    private boolean testModeEnabled;

    public Courier() {
    }

    public Courier(long id, long userId, CourierStatus status, long currentMapPointId, String vehicleNumber,
                   LocalDateTime updatedAt, String fullName, String email, String currentPointName,
                   Double currentLatitude, Double currentLongitude) {
        this(id, userId, status, currentMapPointId, vehicleNumber, updatedAt, fullName, email, null,
                currentPointName, currentLatitude, currentLongitude, "06:00", "14:00", false);
    }

    public Courier(long id, long userId, CourierStatus status, long currentMapPointId, String vehicleNumber,
                   LocalDateTime updatedAt, String fullName, String email, String phone, String currentPointName,
                   Double currentLatitude, Double currentLongitude) {
        this(id, userId, status, currentMapPointId, vehicleNumber, updatedAt, fullName, email, phone,
                currentPointName, currentLatitude, currentLongitude, "06:00", "14:00", false);
    }

    public Courier(long id, long userId, CourierStatus status, long currentMapPointId, String vehicleNumber,
                   LocalDateTime updatedAt, String fullName, String email, String phone, String currentPointName,
                   Double currentLatitude, Double currentLongitude, String shiftStart, String shiftEnd,
                   boolean testModeEnabled) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.currentMapPointId = currentMapPointId;
        this.vehicleNumber = vehicleNumber;
        this.updatedAt = updatedAt;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.currentPointName = currentPointName;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
        this.testModeEnabled = testModeEnabled;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public CourierStatus getStatus() {
        return status;
    }

    public void setStatus(CourierStatus status) {
        this.status = status;
    }

    public long getCurrentMapPointId() {
        return currentMapPointId;
    }

    public void setCurrentMapPointId(long currentMapPointId) {
        this.currentMapPointId = currentMapPointId;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCurrentPointName() {
        return currentPointName;
    }

    public void setCurrentPointName(String currentPointName) {
        this.currentPointName = currentPointName;
    }

    public Double getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(Double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public Double getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(Double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public String getShiftStart() {
        return shiftStart;
    }

    public void setShiftStart(String shiftStart) {
        this.shiftStart = shiftStart;
    }

    public String getShiftEnd() {
        return shiftEnd;
    }

    public void setShiftEnd(String shiftEnd) {
        this.shiftEnd = shiftEnd;
    }

    public boolean isTestModeEnabled() {
        return testModeEnabled;
    }

    public void setTestModeEnabled(boolean testModeEnabled) {
        this.testModeEnabled = testModeEnabled;
    }

    @Override
    public String toString() {
        return getFullName() + " (" + status.displayName() + ")";
    }
}
