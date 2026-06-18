package protocol.requests;

public record NotificationReadRequest(String token, long notificationId) {
}
