package protocol.requests;

public record AddressRequest(
        String token,
        Long id,
        String name,
        String addressText,
        long mapPointId,
        String notes
) {
}
