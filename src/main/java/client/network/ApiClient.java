package client.network;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import model.Theme;
import protocol.MessageType;
import protocol.ProtocolJson;
import protocol.ProtocolMessage;
import util.AppConfig;
import util.ValidationException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiClient {
    private final String host;
    private final int port;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ApiClient(AppConfig config) {
        this.host = config.serverHost();
        this.port = config.serverPort();
    }

    public CompletableFuture<ProtocolMessage> send(MessageType type, Object payload) {
        return CompletableFuture.supplyAsync(() -> {
            ProtocolMessage request = ProtocolMessage.of(type, ProtocolJson.toJson(payload));
            try (Socket socket = new Socket(host, port);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(ProtocolJson.write(request));
                writer.newLine();
                writer.flush();
                String response = reader.readLine();
                if (response == null) {
                    throw new ValidationException("Serwer zamknął połączenie bez odpowiedzi.");
                }
                ProtocolMessage message = ProtocolJson.read(response);
                if (message.getType() == MessageType.ERROR_RESPONSE) {
                    throw new ValidationException(messageText(message));
                }
                return message;
            } catch (IOException e) {
                throw new ValidationException("Brak połączenia z serwerem DeliverFlow.");
            }
        }, executor);
    }

    public <T> T data(ProtocolMessage message, Class<T> type) {
        JsonNode data = message.getPayload().get("data");
        if (data == null || data.isNull()) {
            throw new ValidationException("Odpowiedź serwera nie zawiera danych.");
        }
        return ProtocolJson.mapper().convertValue(data, type);
    }

    public <T> List<T> dataList(ProtocolMessage message, Class<T> type) {
        JsonNode data = message.getPayload().get("data");
        JavaType javaType = ProtocolJson.mapper().getTypeFactory().constructCollectionType(List.class, type);
        return ProtocolJson.mapper().convertValue(data, javaType);
    }

    public String responseMessage(ProtocolMessage message) {
        return messageText(message);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public String cssFor(Theme theme) {
        String css = theme == Theme.DARK ? "/css/dark.css" : "/css/light.css";
        return getClass().getResource(css).toExternalForm();
    }

    private String messageText(ProtocolMessage message) {
        JsonNode node = message.getPayload().get("message");
        return node == null ? "Brak komunikatu z serwera." : node.asText();
    }
}
