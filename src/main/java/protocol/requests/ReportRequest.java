package protocol.requests;

public record ReportRequest(
        String token,
        String fileName
) {
}
