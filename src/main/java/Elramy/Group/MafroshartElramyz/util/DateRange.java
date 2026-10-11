package Elramy.Group.MafroshartElramyz.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Optional inclusive day range for list endpoints: missing ends are open. */
public record DateRange(LocalDateTime from, LocalDateTime to) {

    private static final LocalDateTime MIN = LocalDate.of(2000, 1, 1).atStartOfDay();
    private static final LocalDateTime MAX = LocalDate.of(2200, 1, 1).atStartOfDay();

    public static DateRange of(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("تاريخ النهاية قبل تاريخ البداية.");
        }
        return new DateRange(from != null ? from.atStartOfDay() : MIN, to != null ? to.plusDays(1).atStartOfDay() : MAX);
    }
}
