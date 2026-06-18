package util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    public static final DateTimeFormatter DB_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter REPORT_FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    public static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeUtil() {
    }

    public static String nowDb() {
        return LocalDateTime.now().format(DB_FORMAT);
    }

    public static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, DB_FORMAT);
    }

    public static String formatDisplay(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.format(DISPLAY_FORMAT);
    }
}
