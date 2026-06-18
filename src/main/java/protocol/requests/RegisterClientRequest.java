package protocol.requests;

public record RegisterClientRequest(
        String firstName,
        String lastName,
        String email,
        String phone,
        String password,
        String repeatPassword
) {
}
