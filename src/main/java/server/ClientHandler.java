package server;

import protocol.ProtocolJson;
import protocol.ProtocolMessage;
import service.ReportService;
import util.ValidationException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final RequestRouter router;
    private final ReportService reportService;

    public ClientHandler(Socket socket, RequestRouter router, ReportService reportService) {
        this.socket = socket;
        this.router = router;
        this.reportService = reportService;
    }

    @Override
    public void run() {
        try (Socket ignored = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ProtocolMessage response;
                try {
                    ProtocolMessage request = ProtocolJson.read(line);
                    response = router.handle(request);
                } catch (ValidationException e) {
                    response = ProtocolJson.error(e.getMessage());
                }
                writer.write(ProtocolJson.write(response));
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            reportService.error("Rozłączenie klienta lub błąd socketu: " + e.getMessage());
        }
    }
}
