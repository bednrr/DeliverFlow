package protocol.requests;

import model.Theme;

public record AccountUpdateRequest(
        String token,
        String firstName,
        String lastName,
        String email,
        String phone,
        Theme theme,
        String oldPassword,
        String newPassword,
        String repeatNewPassword
) {
}
