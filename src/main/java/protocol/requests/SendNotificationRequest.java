package protocol.requests;

public record SendNotificationRequest(String token, long recipientUserId, String title, String message) {
}
