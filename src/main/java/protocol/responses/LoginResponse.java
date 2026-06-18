package protocol.responses;

import model.User;

public record LoginResponse(boolean success, String message, String token, User user) {
}
