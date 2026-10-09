package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.mapping.ProductMapper;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.models.StockTransaction;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import Elramy.Group.MafroshartElramyz.repository.StockTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InOrder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Stock lives on the variant (V11): movements lock the variant row and write the ledger. */
class StockServiceImplTest {

    private ProductVariantRepository variants;
    private StockTransactionRepository ledger;
    private StockServiceImpl service;
    private EntityManager em;
    private Product product;
    private ProductVariant redSmall;
    private ProductVariant yellowMedium;

    @BeforeEach
    void setUp() {
        variants = mock(ProductVariantRepository.class);
        ledger = mock(StockTransactionRepository.class);
        em = mock(EntityManager.class);
        service = new StockServiceImpl(variants, ledger, mock(UserService.class), em);
        product = Product.builder().id(1L).code("PRD-1").name("لحاف").itemType(ProductType.لحاف)
                .minimumQuantity(5).active(true).build();
        redSmall = ProductVariant.builder().id(11L).product(product).color("أحمر").size("صغير").code("PRD-1-1").quantity(4).build();
        yellowMedium = ProductVariant.builder().id(12L).product(product).color("أصفر").size("وسط").code("PRD-1-2").quantity(3).build();
        product.getVariants().add(redSmall);
        product.getVariants().add(yellowMedium);
        when(em.find(ProductVariant.class, 11L)).thenReturn(redSmall);
        when(em.find(ProductVariant.class, 12L)).thenReturn(yellowMedium);
    }

    @Test
    void purchaseAddsOnlyToThatVariantAndWritesLedgerRow() {
        service.increaseStock(11L, 5, TransactionType.STOCK_IN, new BigDecimal("100"), 9L, "PURCHASE_INVOICE", null);

        assertEquals(9, redSmall.getQuantity());
        assertEquals(3, yellowMedium.getQuantity(), "other variants untouched");
        assertEquals(12, product.totalQuantity());
        ArgumentCaptor<StockTransaction> tx = ArgumentCaptor.forClass(StockTransaction.class);
        verify(ledger).save(tx.capture());
        assertSame(redSmall, tx.getValue().getVariant());
        assertSame(product, tx.getValue().getProduct());
        assertEquals(TransactionType.STOCK_IN, tx.getValue().getTransactionType());
        assertEquals(5, tx.getValue().getQuantity());
    }

    @Test
    void saleSubtractsFromTheLockedVariant() {
        service.decreaseStock(12L, 3, TransactionType.SALE, new BigDecimal("195"), 4L, "SALES_INVOICE", null);

        assertEquals(0, yellowMedium.getQuantity(), "selling the last units is allowed");
        assertEquals(4, redSmall.getQuantity());
        InOrder order = inOrder(em);
        order.verify(em).flush();                                              // keep pending edits
        order.verify(em).refresh(yellowMedium, LockModeType.PESSIMISTIC_WRITE); // lock + re-read
    }

    @Test
    void theQuantityIsReReadUnderTheLockNotTakenFromTheCachedCopy() {
        // The invoice loaded the variant when it had 4; another cashier sold 3
        // meanwhile. The locked re-read sees 1, so selling 2 must fail instead
        // of writing 4 - 2 = 2 over the other sale.
        doAnswer(inv -> { redSmall.setQuantity(1); return null; })
                .when(em).refresh(redSmall, LockModeType.PESSIMISTIC_WRITE);

        assertThrows(IllegalArgumentException.class,
                () -> service.decreaseStock(11L, 2, TransactionType.SALE, null, null, null, null));
        assertEquals(1, redSmall.getQuantity());
        verify(ledger, never()).save(any());
    }

    @Test
    void insufficientVariantStockIsRejectedEvenIfTheProductHasMore() {
        // Product total is 7, but yellow/medium only has 3.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.decreaseStock(12L, 4, TransactionType.SALE, null, 4L, "SALES_INVOICE", null));

        assertTrue(ex.getMessage().contains("Available: 3"));
        assertTrue(ex.getMessage().contains("أصفر / وسط"));
        assertEquals(3, yellowMedium.getQuantity());
        verify(variants, never()).save(any());
        verify(ledger, never()).save(any());
    }

    @Test
    void zeroNegativeOrMissingQuantityIsRejected() {
        for (Integer q : new Integer[]{0, -1, null}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.increaseStock(11L, q, TransactionType.STOCK_IN, null, null, null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> service.decreaseStock(11L, q, TransactionType.SALE, null, null, null, null));
        }
        verify(ledger, never()).save(any());
    }

    @Test
    void unknownVariantIs404() {
        when(em.find(ProductVariant.class, 99L)).thenReturn(null);
        assertThrows(ResourceNotFoundException.class,
                () -> service.decreaseStock(99L, 1, TransactionType.SALE, null, null, null, null));
    }

    @Test
    void productResponseSumsVariantsAndFlagsLowStockOnTheTotal() {
        ProductMapper mapper = new ProductMapper();
        assertEquals(7, mapper.toResponse(product).quantity());
        assertEquals(2, mapper.toResponse(product).variants().size());
        assertFalse(mapper.toResponse(product).lowStock(), "7 > minimum 5");
        yellowMedium.setQuantity(1);
        assertTrue(mapper.toResponse(product).lowStock(), "total 5 is at the minimum");
        yellowMedium.setActive(false);
        assertEquals(4, mapper.toResponse(product).quantity(), "inactive variants don't count");
        assertEquals(1, mapper.toResponse(product).variants().size());
    }
}
