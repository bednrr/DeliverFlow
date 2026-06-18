package client.map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import protocol.ProtocolJson;
import util.AppConfig;
import util.ValidationException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GeocodingService {
    private static final long MIN_REQUEST_INTERVAL_MS = 1100;

    private final HttpClient httpClient;
    private final AppConfig config;
    private final Map<String, GeoCoordinate> cache = new ConcurrentHashMap<>();
    private final Map<String, List<AddressSuggestion>> suggestionCache = new ConcurrentHashMap<>();
    private final Object rateLimitLock = new Object();
    private long lastRequestAtMillis;

    public GeocodingService(AppConfig config) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), config);
    }

    GeocodingService(HttpClient httpClient, AppConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }

    public GeoCoordinate geocode(String rawQuery) {
        String normalized = normalizeQuery(rawQuery);
        String cacheKey = normalized.toLowerCase(Locale.ROOT);
        GeoCoordinate cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ValidationException lastError = null;
        for (String query : fallbackQueries(normalized)) {
            try {
                List<AddressSuggestion> suggestions = suggest(query, 1);
                if (!suggestions.isEmpty()) {
                    GeoCoordinate coordinate = suggestions.getFirst().coordinate();
                    cache.put(cacheKey, coordinate);
                    return coordinate;
                }
            } catch (ValidationException e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new ValidationException("Nie znaleziono współrzędnych dla adresu: " + normalized + ".");
    }

    public List<AddressSuggestion> suggest(String rawQuery, int limit) {
        String normalized = normalizeQuery(rawQuery);
        int effectiveLimit = Math.max(1, Math.min(limit, 8));
        String cacheKey = normalized.toLowerCase(Locale.ROOT) + "|" + effectiveLimit;
        List<AddressSuggestion> cached = suggestionCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ValidationException lastError = null;
        LinkedHashSet<AddressSuggestion> merged = new LinkedHashSet<>();
        for (String query : fallbackQueries(normalized)) {
            try {
                merged.addAll(fetchSuggestions(query, effectiveLimit));
                if (merged.size() >= effectiveLimit) {
                    break;
                }
            } catch (ValidationException e) {
                lastError = e;
            }
        }
        List<AddressSuggestion> suggestions = merged.stream()
                .limit(effectiveLimit)
                .toList();
        suggestionCache.put(cacheKey, suggestions);
        if (!suggestions.isEmpty()) {
            cache.putIfAbsent(normalized.toLowerCase(Locale.ROOT), suggestions.getFirst().coordinate());
        }
        if (suggestions.isEmpty() && lastError != null) {
            throw lastError;
        }
        return suggestions;
    }

    private List<AddressSuggestion> fetchSuggestions(String query, int limit) {
        throttle();
        HttpRequest request = HttpRequest.newBuilder(buildUri(query, limit))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Accept-Language", "pl,en")
                .header("User-Agent", config.onlineMapUserAgent())
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new ValidationException("Usługa geokodowania zwróciła kod HTTP " + response.statusCode() + ".");
            }
            return parseSuggestions(response.body(), query);
        } catch (IOException e) {
            throw new ValidationException("Nie udało się połączyć z usługą geokodowania Nominatim.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException("Przerwano geokodowanie adresu.");
        }
    }

    static GeoCoordinate parseCoordinate(String responseBody, String query) {
        List<AddressSuggestion> suggestions = parseSuggestions(responseBody, query);
        if (suggestions.isEmpty()) {
            throw new ValidationException("Nie znaleziono współrzędnych dla adresu: " + query + ".");
        }
        return suggestions.getFirst().coordinate();
    }

    static List<AddressSuggestion> parseSuggestions(String responseBody, String query) {
        try {
            JsonNode root = ProtocolJson.mapper().readTree(responseBody);
            if (!root.isArray() || root.isEmpty()) {
                throw new ValidationException("Nie znaleziono współrzędnych dla adresu: " + query + ".");
            }
            List<AddressSuggestion> suggestions = new ArrayList<>();
            for (JsonNode node : root) {
                double latitude = parseDouble(node.path("lat").asText());
                double longitude = parseDouble(node.path("lon").asText());
                JsonNode address = node.path("address");
                String street = firstNonBlank(
                        address.path("road").asText(),
                        address.path("pedestrian").asText(),
                        address.path("footway").asText(),
                        address.path("residential").asText(),
                        address.path("suburb").asText());
                String houseNumber = address.path("house_number").asText();
                String postalCode = address.path("postcode").asText();
                String city = firstNonBlank(
                        address.path("city").asText(),
                        address.path("town").asText(),
                        address.path("village").asText(),
                        "Kraków");
                String displayName = formatDisplayName(street, houseNumber, postalCode, city, node.path("display_name").asText());
                suggestions.add(new AddressSuggestion(displayName, street, houseNumber, postalCode, city,
                        new GeoCoordinate(latitude, longitude)));
            }
            return suggestions;
        } catch (JsonProcessingException e) {
            throw new ValidationException("Niepoprawna odpowiedź geokodowania Nominatim.");
        }
    }

    private URI buildUri(String query, int limit) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return URI.create(config.onlineMapNominatimUrl()
                + "?q=" + encoded
                + "&format=jsonv2&limit=" + limit
                + "&addressdetails=1&countrycodes=pl&dedupe=1");
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null) {
            throw new ValidationException("Adres jest wymagany.");
        }
        String normalized = rawQuery.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new ValidationException("Adres jest wymagany.");
        }
        return normalized;
    }

    private void throttle() {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long waitMs = MIN_REQUEST_INTERVAL_MS - (now - lastRequestAtMillis);
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ValidationException("Przerwano oczekiwanie na geokodowanie.");
                }
            }
            lastRequestAtMillis = System.currentTimeMillis();
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ValidationException("Usługa geokodowania zwróciła niepoprawne współrzędne.");
        }
    }

    private List<String> fallbackQueries(String normalized) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        variants.add(normalized);
        variants.add(normalized.replaceAll("(\\d+[A-Za-z]?)\\s*/\\s*\\d+[A-Za-z]?", "$1"));
        variants.add(stripStreetPrefixes(normalized));
        variants.add(stripStreetPrefixes(normalized.replaceAll("(\\d+[A-Za-z]?)\\s*/\\s*\\d+[A-Za-z]?", "$1")));
        return variants.stream()
                .map(value -> value.replaceAll("\\s+", " ").trim())
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String stripStreetPrefixes(String value) {
        return value.replaceAll("(?i)\\bul\\.\\s*", "")
                .replaceAll("(?i)\\bal\\.\\s*", "")
                .replaceAll("(?i)\\baleja\\s+", "")
                .replaceAll("(?i)\\bos\\.\\s*", "");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String formatDisplayName(String street, String houseNumber, String postalCode, String city, String fallback) {
        String streetPart = firstNonBlank(street, fallback);
        String numberPart = houseNumber == null || houseNumber.isBlank() ? "" : " " + houseNumber.trim();
        String postalPart = postalCode == null || postalCode.isBlank() ? "" : postalCode.trim() + " ";
        String cityPart = city == null || city.isBlank() ? "Kraków" : city.trim();
        String label = (streetPart + numberPart + ", " + postalPart + cityPart).replaceAll("\\s+", " ").trim();
        return label.endsWith(",") ? label.substring(0, label.length() - 1) : label;
    }
}
