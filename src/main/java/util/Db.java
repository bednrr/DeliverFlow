package util;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public final class Db {
    private Db() {
    }

    public static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        return TimeUtil.parse(rs.getString(column));
    }

    public static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? BigDecimal.ZERO : new BigDecimal(value);
    }
}
