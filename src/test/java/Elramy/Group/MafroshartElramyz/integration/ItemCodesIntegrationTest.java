package Elramy.Group.MafroshartElramyz.integration;

import Elramy.Group.MafroshartElramyz.dto.product.CreateProductRequest;
import Elramy.Group.MafroshartElramyz.dto.product.ProductVariantRequest;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import Elramy.Group.MafroshartElramyz.repository.UserRepository;
import Elramy.Group.MafroshartElramyz.services.ItemCodes;
import Elramy.Group.MafroshartElramyz.services.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** V27 codes on a real database: format, uniqueness, code = barcode, and old codes still scanning. */
@SpringBootTest
class ItemCodesIntegrationTest {

    @Autowired private ItemCodes itemCodes;
    @Autowired private ProductService products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generatedCodesAreSixSafeCharactersAndNeverRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String c = itemCodes.next();
            assertTrue(ItemCodes.FORMAT.matcher(c).matches(), c);
            assertTrue(seen.add(c), "repeated " + c);
        }
        // The mapping itself is one-to-one: 200,000 consecutive numbers, 200,000 codes.
        Integer distinct = jdbc.queryForObject("SELECT COUNT(DISTINCT elramy_item_code(g)) FROM generate_series(1, 200000) g", Integer.class);
        assertEquals(200000, distinct);
    }

    @Test
    void aProductSharesItsCodeWithItsFirstColourSizeAndEveryCodeIsTheBarcode() {
        String admin = users.findAll().stream().filter(u -> u.getRole().name().equals("ADMIN")).findFirst().orElseThrow().getUsername();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        var p = products.create(new CreateProductRequest("كود " + System.nanoTime(), null, "لحاف", 0,
                List.of(new ProductVariantRequest(null, "أحمر", "كبير"), new ProductVariantRequest(null, "أبيض", "كبير"))));

        assertTrue(ItemCodes.FORMAT.matcher(p.code()).matches(), p.code());
        assertEquals(p.code(), p.variants().get(0).code());
        assertNotEquals(p.code(), p.variants().get(1).code());
        p.variants().forEach(v -> assertEquals(v.code(), v.barcode()));

        // An old printed code (alias) and a loosely typed new one both find the item.
        Long first = p.variants().get(0).id();
        jdbc.update("INSERT INTO item_code_aliases (code, variant_id) VALUES (?, ?)", "PRD-OLD-" + first, first);
        assertEquals(first, variants.findByScanCode("PRD-OLD-" + first).orElseThrow().getId());
        assertEquals(first, variants.findByScanCode(" " + p.code().toLowerCase() + " ").orElseThrow().getId());
    }
}
