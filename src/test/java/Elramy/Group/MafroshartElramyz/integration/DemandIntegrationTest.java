package Elramy.Group.MafroshartElramyz.integration;

import Elramy.Group.MafroshartElramyz.dto.customer.CustomerInvoiceRequest;
import Elramy.Group.MafroshartElramyz.dto.product.CreateProductRequest;
import Elramy.Group.MafroshartElramyz.dto.product.ProductVariantRequest;
import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceItemRequest;
import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceRequest;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.reports.DemandService;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.UserRepository;
import Elramy.Group.MafroshartElramyz.services.ProductService;
import Elramy.Group.MafroshartElramyz.services.SalesInvoiceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A real sale shows up in the demand analysis (the SQL runs on PostgreSQL). */
@SpringBootTest
class DemandIntegrationTest {

    @Autowired private DemandService demand;
    @Autowired private ProductService products;
    @Autowired private SalesInvoiceService sales;
    @Autowired private UserRepository users;
    @Autowired private BranchRepository branches;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aSaleCountsForItsColourSize() {
        String admin = users.findAll().stream().filter(u -> u.getRole().name().equals("ADMIN")).findFirst().orElseThrow().getUsername();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Long branch = branches.findAll().get(0).getId();

        var p = products.create(new CreateProductRequest("طلب " + System.nanoTime(), null, "لحاف", 0,
                List.of(new ProductVariantRequest(null, "أحمر", null, 10), new ProductVariantRequest(null, "أبيض", null, 4))));
        products.setPrice(p.id(), new Elramy.Group.MafroshartElramyz.dto.product.CreateProductPriceRequest(new BigDecimal("60"), null, new BigDecimal("100")));
        Long red = p.variants().get(0).id();

        sales.create(new SalesInvoiceRequest(branch, new CustomerInvoiceRequest("عميل طلب", "0109" + (System.nanoTime() % 10_000_000), null),
                PaymentMethod.CASH, List.of(new SalesInvoiceItemRequest(null, red, 3, BigDecimal.ZERO)),
                new BigDecimal("30"), new BigDecimal("270"), null, null));

        DemandService.Item r = demand.build(30).items().stream().filter(i -> i.variantId().equals(red)).findFirst().orElseThrow();
        assertEquals(3, r.sold());
        assertEquals(7, r.stock());
        assertEquals(0, new BigDecimal("270").compareTo(r.revenue()), "line share after the 30 invoice discount");
        assertEquals(3, r.secondHalf(), "sold just now: second half of the period");
        assertNotNull(r.lastSaleAt());

        DemandService.Item white = demand.build(30).items().stream().filter(i -> i.variantId().equals(p.variants().get(1).id())).findFirst().orElseThrow();
        assertEquals(DemandService.Status.NOT_MOVING, white.status());
    }
}
