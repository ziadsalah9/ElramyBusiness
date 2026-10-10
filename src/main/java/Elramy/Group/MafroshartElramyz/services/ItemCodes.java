package Elramy.Group.MafroshartElramyz.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Item codes (V27): 6 characters, a letter then 5 letters/digits with no
 * I, L, O or U. The same text is the code and the barcode of a colour/size;
 * a product takes the code of its first colour/size. Codes come from the
 * database (item_code_seq + elramy_item_code), so they are unique across
 * every product and colour/size, including ones made by the migration.
 */
@Component
public class ItemCodes {

    /** A code in the current format. */
    public static final Pattern FORMAT = Pattern.compile("^[A-HJKMNP-TV-Z][0-9A-HJKMNP-TV-Z]{5}$");

    @PersistenceContext
    private EntityManager em;

    public String next() {
        return (String) em.createNativeQuery("SELECT elramy_item_code(nextval('item_code_seq'))").getSingleResult();
    }

    /**
     * What a scanned or typed code may stand for, most likely first: as
     * given, upper case, and - for 6 characters - with the look-alikes a
     * person may type (O for 0, I or L for 1), which codes never contain.
     */
    public static Set<String> candidates(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) return out;
        String c = raw.trim();
        if (c.isEmpty()) return out;
        out.add(c);
        String upper = c.toUpperCase(Locale.ROOT);
        out.add(upper);
        if (upper.length() == 6 && upper.matches("[0-9A-Z]{6}")) {
            out.add(upper.charAt(0) + upper.substring(1).replace('O', '0').replace('I', '1').replace('L', '1'));
        }
        return out;
    }
}
