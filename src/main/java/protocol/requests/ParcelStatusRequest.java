package protocol.requests;

import model.ParcelStatus;

public record ParcelStatusRequest(String token, long parcelId, ParcelStatus status, String note) {
}
