package protocol.responses;

public record DataResponse<T>(boolean success, String message, T data) {
}
