package Elramy.Group.MafroshartElramyz.dto.product;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The allowed product attribute values, shared by the Excel template
 * (dropdown lists) and the import validation.
 *
 * Product types and colours are managed in the database (V24, V25).
 */
public final class ProductCatalog {

    private ProductCatalog() {
    }

    // Colours live in the product_colors table (V25, ProductColorService).

    /**
     * Common sizes. Size is free text (typed in the product form and the
     * Excel sheet); these only normalise the old dropdown labels, e.g.
     * "طقم 4 قطع" is stored as "طقم 4", so earlier data still matches.
     */
    public static final Map<String, String> SIZES = linked(
            "صغير", "صغير",
            "وسط", "وسط",
            "كبير", "كبير",
            "مزدوج", "مزدوج",
            "كينج", "كينج",
            "سوبر كينج", "سوبر كينج",
            "قطعة واحدة", "قطعة واحدة",
            "طقم 2", "طقم 2 قطع",
            "طقم 3", "طقم 3 قطع",
            "طقم 4", "طقم 4 قطع",
            "طقم 5", "طقم 5 قطع",
            "طقم 6", "طقم 6 قطع",
            "طقم 7", "طقم 7 قطع",
            "حجم واحد", "حجم واحد"
    );

    /** Longest size that fits product_variants.size / product_draft_variants.size. */
    public static final int MAX_SIZE_LENGTH = 50;

    /**
     * A typed size: trimmed with single spaces, blank = no size. Old
     * dropdown labels map to their stored value; anything else is kept as
     * typed. Throws when it does not fit the column.
     */
    public static String sizeText(String raw) {
        String v = normalize(raw);
        if (v.isEmpty()) return null;
        String size = resolve(SIZES, v).orElse(v);
        if (size.length() > MAX_SIZE_LENGTH) {
            throw new IllegalArgumentException("المقاس أطول من " + MAX_SIZE_LENGTH + " حرف: \"" + v.substring(0, 20) + "…\"");
        }
        return size;
    }

    // Product types live in the product_types table (V24, ItemTypeService).


    /** Resolves a size from its stored value or its old dropdown label. */
    public static Optional<String> resolve(Map<String, String> catalog, String raw) {
        String v = normalize(raw);
        if (v.isEmpty()) return Optional.empty();
        for (Map.Entry<String, String> e : catalog.entrySet()) {
            if (normalize(e.getKey()).equals(v) || normalize(e.getValue()).equals(v)) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    /** Trims and collapses inner whitespace, so "طقم  3" matches "طقم 3". */
    public static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static Map<String, String> linked(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return java.util.Collections.unmodifiableMap(m);
    }
}
