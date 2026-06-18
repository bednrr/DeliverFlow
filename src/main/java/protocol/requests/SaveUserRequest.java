package protocol.requests;

import model.Theme;
import model.UserRole;

public record SaveUserRequest(
        String token,
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String password,
        UserRole role,
        Theme theme,
        boolean blocked,
        String vehicleNumber,
        Long currentMapPointId,
        String shiftStart,
        String shiftEnd,
        boolean courierTestModeEnabled
) {
}
