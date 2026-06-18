package protocol.responses;

import model.CourierStatus;
import model.ParcelStatus;

import java.time.LocalDateTime;

public record FleetSnapshotEntry(
        long courierId,
        long courierUserId,
        String courierName,
        CourierStatus courierStatus,
        String vehicleNumber,
        long currentMapPointId,
        double latitude,
        double longitude,
        String locationLabel,
        LocalDateTime updatedAt,
        Long activeParcelId,
        ParcelStatus activeParcelStatus,
        int activeParcelCount,
        FleetTaskPhase phase,
        Long targetMapPointId
) {
}
