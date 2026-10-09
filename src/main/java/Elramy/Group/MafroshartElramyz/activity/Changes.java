package Elramy.Group.MafroshartElramyz.activity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Collects field changes for the activity log. Only real changes are kept:
 * equal values (including 150 vs 150.00, null vs blank) are ignored.
 */
public final class Changes {

    public record Change(String field, String before, String after) {
    }

    private final List<Change> list = new ArrayList<>();

    public static Changes of() {
        return new Changes();
    }

    /** Records field: before -> after when they differ. */
    public Changes add(String field, Object before, Object after) {
        String b = text(before);
        String a = text(after);
        if (!Objects.equals(b, a)) list.add(new Change(field, b, a));
        return this;
    }

    /** Records a value that has no "before" (e.g. a new item). */
    public Changes set(String field, Object value) {
        String v = text(value);
        if (v != null) list.add(new Change(field, null, v));
        return this;
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public List<Change> list() {
        return Collections.unmodifiableList(list);
    }

    static String text(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal d) return d.stripTrailingZeros().toPlainString();
        if (v instanceof Boolean b) return b ? "نعم" : "لا";
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
