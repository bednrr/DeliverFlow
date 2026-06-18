package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Parcel {
    private long id;
    private String title;
    private String description;
    private ParcelSize size;
    private double weightKg;
    private long senderUserId;
    private String senderName;
    private String senderPhone;
    private String receiverName;
    private String receiverPhone;
    private String senderAddressText;
    private long senderMapPointId;
    private String senderMapPointName;
    private String receiverAddressText;
    private long receiverMapPointId;
    private String receiverMapPointName;
    private ParcelStatus status;
    private Long assignedCourierId;
    private String assignedCourierName;
    private String assignedCourierPhone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal estimatedPrice;

    public Parcel() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ParcelSize getSize() {
        return size;
    }

    public void setSize(ParcelSize size) {
        this.size = size;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }

    public long getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(long senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderPhone() {
        return senderPhone;
    }

    public void setSenderPhone(String senderPhone) {
        this.senderPhone = senderPhone;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getSenderAddressText() {
        return senderAddressText;
    }

    public void setSenderAddressText(String senderAddressText) {
        this.senderAddressText = senderAddressText;
    }

    public long getSenderMapPointId() {
        return senderMapPointId;
    }

    public void setSenderMapPointId(long senderMapPointId) {
        this.senderMapPointId = senderMapPointId;
    }

    public String getSenderMapPointName() {
        return senderMapPointName;
    }

    public void setSenderMapPointName(String senderMapPointName) {
        this.senderMapPointName = senderMapPointName;
    }

    public String getReceiverAddressText() {
        return receiverAddressText;
    }

    public void setReceiverAddressText(String receiverAddressText) {
        this.receiverAddressText = receiverAddressText;
    }

    public long getReceiverMapPointId() {
        return receiverMapPointId;
    }

    public void setReceiverMapPointId(long receiverMapPointId) {
        this.receiverMapPointId = receiverMapPointId;
    }

    public String getReceiverMapPointName() {
        return receiverMapPointName;
    }

    public void setReceiverMapPointName(String receiverMapPointName) {
        this.receiverMapPointName = receiverMapPointName;
    }

    public ParcelStatus getStatus() {
        return status;
    }

    public void setStatus(ParcelStatus status) {
        this.status = status;
    }

    public Long getAssignedCourierId() {
        return assignedCourierId;
    }

    public void setAssignedCourierId(Long assignedCourierId) {
        this.assignedCourierId = assignedCourierId;
    }

    public String getAssignedCourierName() {
        return assignedCourierName;
    }

    public void setAssignedCourierName(String assignedCourierName) {
        this.assignedCourierName = assignedCourierName;
    }

    public String getAssignedCourierPhone() {
        return assignedCourierPhone;
    }

    public void setAssignedCourierPhone(String assignedCourierPhone) {
        this.assignedCourierPhone = assignedCourierPhone;
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

    public BigDecimal getEstimatedPrice() {
        return estimatedPrice;
    }

    public void setEstimatedPrice(BigDecimal estimatedPrice) {
        this.estimatedPrice = estimatedPrice;
    }
}
