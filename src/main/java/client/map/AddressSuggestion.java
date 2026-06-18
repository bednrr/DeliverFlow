package client.map;

public record AddressSuggestion(String displayName,
                                String street,
                                String houseNumber,
                                String postalCode,
                                String city,
                                GeoCoordinate coordinate) {
    public String normalizedCity() {
        if (city == null || city.isBlank()) {
            return "Kraków";
        }
        return city;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
