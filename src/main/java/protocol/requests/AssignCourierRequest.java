package protocol.requests;

public record AssignCourierRequest(String token, long parcelId, long courierId) {
}
