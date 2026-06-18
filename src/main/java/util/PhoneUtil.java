package util;

public final class PhoneUtil {
    private PhoneUtil() {
    }

    public static String normalize(String phone) {
        return phone == null ? "" : phone.replaceAll("[\\s-]", "");
    }

    public static boolean isValid(String phone) {
        return normalize(phone).matches("\\d{9}");
    }

    public static String requireValid(String phone) {
        String normalized = normalize(phone);
        if (!normalized.matches("\\d{9}")) {
            throw new ValidationException("Numer telefonu musi mieć 9 cyfr.");
        }
        return normalized;
    }
}
