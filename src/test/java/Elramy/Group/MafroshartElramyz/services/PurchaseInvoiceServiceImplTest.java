package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.enums.Role;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.dto.purchaseInvoice.CreatePurchaseInvoiceRequest;
import Elramy.Group.MafroshartElramyz.dto.purchaseInvoice.PurchaseInvoiceItemRequest;
import Elramy.Group.MafroshartElramyz.exception.DuplicatePurchaseInvoiceException;
import Elramy.Group.MafroshartElramyz.mapping.PurchaseInvoiceMapper;
import Elramy.Group.MafroshartElramyz.models.*;
import Elramy.Group.MafroshartElramyz.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A purchase adds stock per colour/size and stores rounded totals. */
class PurchaseInvoiceServiceImplTest {

    private PurchaseInvoiceRepository invoices;
    private StockService stock;
    private PurchaseInvoiceServiceImpl service;

    @BeforeEach
    void setUp() {
        invoices = mock(PurchaseInvoiceRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        stock = mock(StockService.class);
        UserService users = mock(UserService.class);
        service = new PurchaseInvoiceServiceImpl(invoices, mock(ProductRepository.class), variants, branches,
                mock(PurchaseInvoiceMapper.class), stock, users, mock(ActivityLogService.class));

        User admin = new User();
        admin.setRole(Role.ADMIN);
        when(users.getCurrentUser()).thenReturn(admin);
        when(branches.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).name("فرع").active(true).build()));
        Product quilt = Product.builder().id(1L).name("لحاف").active(true).build();
        when(variants.findById(11L)).thenReturn(Optional.of(ProductVariant.builder().id(11L).product(quilt).active(true).quantity(0).build()));
        when(invoices.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CreatePurchaseInvoiceRequest purchase(String number, String discount, String paid, PurchaseInvoiceItemRequest... items) {
        return new CreatePurchaseInvoiceRequest(number, 1L, new BigDecimal(discount), new BigDecimal(paid), PaymentMethod.CASH, null, List.of(items));
    }

    @Test
    void stockGoesUpAndTotalsAreRounded() {
        service.create(purchase("P-1", "0.005", "0", new PurchaseInvoiceItemRequest(null, 11L, 3, new BigDecimal("33.333"), BigDecimal.ZERO)));

        ArgumentCaptor<PurchaseInvoice> c = ArgumentCaptor.forClass(PurchaseInvoice.class);
        verify(invoices).save(c.capture());
        assertEquals(0, new BigDecimal("100.00").compareTo(c.getValue().getItems().get(0).getTotal()));   // 99.999 -> 100.00
        assertEquals(2, c.getValue().getTotal().scale());
        verify(stock).increaseStock(eq(11L), eq(3), eq(TransactionType.STOCK_IN), any(), any(), anyString(), anyString());
    }

    @Test
    void duplicateNumbersAndOverpaymentAreRejected() {
        when(invoices.existsByInvoiceNumber("P-1")).thenReturn(true);
        assertThrows(DuplicatePurchaseInvoiceException.class,
                () -> service.create(purchase("P-1", "0", "0", new PurchaseInvoiceItemRequest(null, 11L, 1, BigDecimal.TEN, BigDecimal.ZERO))));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(purchase("P-2", "0", "11", new PurchaseInvoiceItemRequest(null, 11L, 1, BigDecimal.TEN, BigDecimal.ZERO))));
        verifyNoInteractions(stock);
    }
}
