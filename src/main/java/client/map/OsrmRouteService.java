package client.map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import protocol.ProtocolJson;
import util.AppConfig;
import util.ValidationException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OsrmRouteService {
    private final HttpClient httpClient;
    private final AppConfig config;

    public OsrmRouteService(AppConfig config) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), config);
    }

    OsrmRouteService(HttpClient httpClient, AppConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }

    public OsrmRoute fetchRoute(double latA, double lngA, double latB, double lngB) {
        HttpRequest request = HttpRequest.newBuilder(buildUri(latA, lngA, latB, lngB))
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", config.onlineMapUserAgent())
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new ValidationException("Usługa trasowania OSRM zwróciła kod HTTP " + response.statusCode() + ".");
            }
            return parseRoute(response.body());
        } catch (IOException e) {
            throw new ValidationException("Nie udało się połączyć z usługą trasowania OSRM.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException("Przerwano pobieranie trasy.");
        }
    }

    static OsrmRoute parseRoute(String responseBody) {
        try {
            JsonNode root = ProtocolJson.mapper().readTree(responseBody);
            if (!"Ok".equals(root.path("code").asText())) {
                String code = root.path("code").asText("Brak kodu");
                throw new ValidationException("OSRM nie wyznaczył trasy: " + code + ".");
            }
            JsonNode route = root.path("routes").path(0);
            if (route.isMissingNode() || route.isNull()) {
                throw new ValidationException("OSRM nie zwrócił żadnej trasy.");
            }
            JsonNode coordinatesNode = route.path("geometry").path("coordinates");
            if (!coordinatesNode.isArray() || coordinatesNode.isEmpty()) {
                throw new ValidationException("OSRM zwrócił trasę bez geometrii.");
            }
            List<GeoCoordinate> geometry = new ArrayList<>();
            for (JsonNode coordinateNode : coordinatesNode) {
                if (!coordinateNode.isArray() || coordinateNode.size() < 2) {
                    continue;
                }
                double longitude = coordinateNode.get(0).asDouble();
                double latitude = coordinateNode.get(1).asDouble();
                geometry.add(new GeoCoordinate(latitude, longitude));
            }
            if (geometry.isEmpty()) {
                throw new ValidationException("OSRM zwrócił pustą geometrię trasy.");
            }
            double durationSeconds = route.path("duration").asDouble(-1);
            double distanceMeters = route.path("distance").asDouble(-1);
            if (durationSeconds < 0 || distanceMeters < 0) {
                throw new ValidationException("OSRM zwrócił niepełne dane o trasie.");
            }
            return new OsrmRoute(geometry, durationSeconds, distanceMeters);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Niepoprawna odpowiedź trasowania OSRM.");
        }
    }

    private URI buildUri(double latA, double lngA, double latB, double lngB) {
        return URI.create(String.format(Locale.US,
                "%s/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=false",
                config.onlineMapOsrmUrl(),
                lngA, latA,
                lngB, latB));
    }
}
