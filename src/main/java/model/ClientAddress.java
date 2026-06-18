package model;

import java.time.LocalDateTime;

public class ClientAddress {
    private long id;
    private long userId;
    private String name;
    private String addressText;
    private long mapPointId;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ClientAddress() {
    }

    public ClientAddress(long id, long userId, String name, String addressText, long mapPointId,
                         String notes, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.addressText = addressText;
        this.mapPointId = mapPointId;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddressText() {
        return addressText;
    }

    public void setAddressText(String addressText) {
        this.addressText = addressText;
    }

    public long getMapPointId() {
        return mapPointId;
    }

    public void setMapPointId(long mapPointId) {
        this.mapPointId = mapPointId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        String safeName = name == null || name.isBlank() ? "Adres" : name;
        String safeAddress = addressText == null || addressText.isBlank() ? "" : " - " + addressText;
        return safeName + safeAddress;
    }
}
