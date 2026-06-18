package model;

import java.time.LocalDateTime;

public class ParcelStatusHistory {
    private long id;
    private long parcelId;
    private ParcelStatus status;
    private String note;
    private LocalDateTime createdAt;

    public ParcelStatusHistory() {
    }

    public ParcelStatusHistory(long id, long parcelId, ParcelStatus status, String note, LocalDateTime createdAt) {
        this.id = id;
        this.parcelId = parcelId;
        this.status = status;
        this.note = note;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getParcelId() {
        return parcelId;
    }

    public void setParcelId(long parcelId) {
        this.parcelId = parcelId;
    }

    public ParcelStatus getStatus() {
        return status;
    }

    public void setStatus(ParcelStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
