package model;

import java.time.LocalDateTime;

public class Notification {
    private long id;
    private long senderUserId;
    private String senderName;
    private long recipientUserId;
    private String recipientName;
    private String title;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    public Notification() {
    }

    public Notification(long id, long senderUserId, String senderName, long recipientUserId, String recipientName,
                        String title, String message, LocalDateTime createdAt, LocalDateTime readAt) {
        this.id = id;
        this.senderUserId = senderUserId;
        this.senderName = senderName;
        this.recipientUserId = recipientUserId;
        this.recipientName = recipientName;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public long getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(long recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
