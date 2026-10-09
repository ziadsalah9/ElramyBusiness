package Elramy.Group.MafroshartElramyz.integration;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import Elramy.Group.MafroshartElramyz.repository.UserRepository;
import Elramy.Group.MafroshartElramyz.services.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two cashiers sell the same colour/size at the same moment, each having
 * loaded the variant before taking the lock (exactly what an invoice does).
 * Needs real PostgreSQL row locks: it runs on the throwaway Testcontainers
 * database that every test uses (see the surefire settings in pom.xml).
 */
@SpringBootTest
class ConcurrentSaleIntegrationTest {

    @Autowired private StockService stockService;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private UserRepository users;
    @Autowired private TransactionTemplate tx;

    @Test
    void concurrentSalesBothCountAndNeitherIsLost() throws Exception {
        String admin = users.findAll().stream().filter(u -> u.getRole().name().equals("ADMIN")).findFirst().orElseThrow().getUsername();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Long variantId = tx.execute(s -> {
            Product p = products.save(Product.builder().code("IT-" + tag).name("IT " + tag).itemType(ProductType.لحاف)
                    .minimumQuantity(0).sellingPrice(new BigDecimal("10")).active(true).build());
            return variants.save(ProductVariant.builder().product(p).code("IT-" + tag + "-1").barcode("IT" + tag)
                    .quantity(10).active(true).build()).getId();
        });

        CountDownLatch bothLoaded = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Runnable cashier = () -> {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            tx.executeWithoutResult(s -> {
                variants.findById(variantId).orElseThrow().getQuantity();   // stale read, like the invoice loop
                bothLoaded.countDown();
                try { bothLoaded.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                stockService.decreaseStock(variantId, 3, TransactionType.SALE, BigDecimal.TEN, null, "IT", "concurrency test");
            });
        };
        List<Future<?>> runs = List.of(pool.submit(cashier), pool.submit(cashier));
        for (Future<?> f : runs) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(4, variants.findById(variantId).orElseThrow().getQuantity(), "10 - 3 - 3; a lost update would leave 7");
    }
}
