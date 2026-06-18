package protocol.requests;

import model.CourierStatus;

public record CourierStatusRequest(String token, long courierId, CourierStatus status) {
}
