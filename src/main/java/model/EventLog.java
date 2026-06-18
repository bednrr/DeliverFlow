package model;

import java.time.LocalDateTime;

public class EventLog {
    private long id;
    private String level;
    private String message;
    private LocalDateTime createdAt;

    public EventLog() {
    }

    public EventLog(long id, String level, String message, LocalDateTime createdAt) {
        this.id = id;
        this.level = level;
        this.message = message;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
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
}
