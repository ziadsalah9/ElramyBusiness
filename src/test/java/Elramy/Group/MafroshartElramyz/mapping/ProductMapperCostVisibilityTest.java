package Elramy.Group.MafroshartElramyz.mapping;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import Elramy.Group.MafroshartElramyz.dto.product.ProductPriceResponse;
import Elramy.Group.MafroshartElramyz.models.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Cost price and profit % are ADMIN-only in every product response. */
class ProductMapperCostVisibilityTest {

    private final ProductMapper mapper = new ProductMapper();
    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(1L).code("PRD-1").name("لحاف").itemType(ProductType.لحاف)
                .purchasePrice(new BigDecimal("150.00")).profitPercentage(new BigDecimal("30.00"))
                .sellingPrice(new BigDecimal("195.00")).active(true).build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    void employeeSeesTheSellingPriceButNotCostOrProfit() {
        loginAs("ROLE_EMPLOYEE");
        ProductPriceResponse price = mapper.toResponse(product).currentPrice();
        assertEquals(0, new BigDecimal("195.00").compareTo(price.sellingPrice()));
        assertNull(price.purchasePrice());
        assertNull(price.profitPercentage());
        assertNull(mapper.toPriceResponse(product).purchasePrice(), "the /price endpoint too");
    }

    @Test
    void adminSeesEverything() {
        loginAs("ROLE_ADMIN");
        ProductPriceResponse price = mapper.toResponse(product).currentPrice();
        assertEquals(0, new BigDecimal("150.00").compareTo(price.purchasePrice()));
        assertEquals(0, new BigDecimal("30.00").compareTo(price.profitPercentage()));
    }

    @Test
    void withoutAUserCostStaysHidden() {
        assertNull(mapper.toResponse(product).currentPrice().purchasePrice());
        assertNotNull(mapper.toResponse(product).currentPrice().sellingPrice());
    }
}
