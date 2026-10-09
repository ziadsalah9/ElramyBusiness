package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.enums.Role;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.dto.customer.CustomerInvoiceRequest;
import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceItemRequest;
import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceRequest;
import Elramy.Group.MafroshartElramyz.mapping.SalesInvoiceMapper;
import Elramy.Group.MafroshartElramyz.models.*;
import Elramy.Group.MafroshartElramyz.repository.*;
import Elramy.Group.MafroshartElramyz.settings.StoreSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Totals, discounts, payment and stock of a new sale - all computed on the server. */
class SalesInvoiceServiceImplTest {

    private SalesInvoiceRepository invoices;
    private CustomerRepository customers;
    private StockService stock;
    private UserService users;
    private SalesInvoiceServiceImpl service;
    private User admin;
    private User employee;
    private Elramy.Group.MafroshartElramyz.installment.InstallmentService installments;

    @BeforeEach
    void setUp() {
        invoices = mock(SalesInvoiceRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        customers = mock(CustomerRepository.class);
        stock = mock(StockService.class);
        users = mock(UserService.class);
        StoreSettingsService settings = mock(StoreSettingsService.class);
        installments = mock(Elramy.Group.MafroshartElramyz.installment.InstallmentService.class);
        when(settings.employeeMaxDiscountPercent()).thenReturn(BigDecimal.TEN);
        service = new SalesInvoiceServiceImpl(invoices, mock(ProductRepository.class), variants, branches, stock,
                mock(SalesInvoiceMapper.class), customers, users, mock(ActivityLogService.class), settings, installments);

        Branch branch = Branch.builder().id(1L).name("فرع").active(true).build();
        when(branches.findById(1L)).thenReturn(Optional.of(branch));
        admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        employee = new User();
        employee.setId(2L);
        employee.setRole(Role.EMPLOYEE);
        employee.setBranch(branch);
        when(users.getCurrentUser()).thenReturn(admin);

        Product quilt = Product.builder().id(1L).name("لحاف").sellingPrice(new BigDecimal("100")).active(true).build();
        Product towel = Product.builder().id(2L).name("فوطة").sellingPrice(new BigDecimal("50")).active(true).build();
        when(variants.findById(11L)).thenReturn(Optional.of(ProductVariant.builder().id(11L).product(quilt).active(true).quantity(10).build()));
        when(variants.findById(12L)).thenReturn(Optional.of(ProductVariant.builder().id(12L).product(towel).active(true).quantity(10).build()));
        when(customers.findByPhone("010")).thenReturn(Optional.of(Customer.builder().id(5L).name("محمد").phone("010").active(true).build()));
        when(invoices.existsByInvoiceNumber(anyString())).thenReturn(false);
        when(invoices.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static SalesInvoiceRequest sale(PaymentMethod method, String invoiceDiscount, String paid, String cash, SalesInvoiceItemRequest... items) {
        return new SalesInvoiceRequest(1L, new CustomerInvoiceRequest("محمد", "010", null), method, List.of(items),
                new BigDecimal(invoiceDiscount), new BigDecimal(paid), null, cash == null ? null : new BigDecimal(cash));
    }

    private static SalesInvoiceItemRequest item(long variantId, int qty, String discount) {
        return new SalesInvoiceItemRequest(null, variantId, qty, new BigDecimal(discount));
    }

    private SalesInvoice saved() {
        ArgumentCaptor<SalesInvoice> c = ArgumentCaptor.forClass(SalesInvoice.class);
        verify(invoices).save(c.capture());
        return c.getValue();
    }

    @Test
    void totalsComeFromTheServerPriceMinusLineAndInvoiceDiscounts() {
        service.create(sale(PaymentMethod.CASH, "20", "200", null, item(11L, 2, "10"), item(12L, 1, "0")));

        SalesInvoice inv = saved();
        assertEquals(0, new BigDecimal("190").compareTo(inv.getItems().get(0).getTotal()));   // 2 x 100 - 10
        assertEquals(0, new BigDecimal("220").compareTo(inv.getTotal()));                     // 190 + 50 - 20
        assertEquals(0, new BigDecimal("200").compareTo(inv.getPaid()));                      // 20 left to pay
    }

    @Test
    void stockIsTakenPerColourSizeInVariantOrder() {
        service.create(sale(PaymentMethod.CASH, "0", "0", null, item(12L, 1, "0"), item(11L, 3, "0")));
        InOrder order = inOrder(stock);
        order.verify(stock).decreaseStock(eq(11L), eq(3), eq(TransactionType.SALE), any(), any(), eq("SALES_INVOICE"), anyString());
        order.verify(stock).decreaseStock(eq(12L), eq(1), eq(TransactionType.SALE), any(), any(), eq("SALES_INVOICE"), anyString());
    }

    @Test
    void paidMoreThanTheTotalOrDiscountsBeyondTheLinesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create(sale(PaymentMethod.CASH, "0", "100.01", null, item(11L, 1, "0"))));
        assertThrows(IllegalArgumentException.class, () -> service.create(sale(PaymentMethod.CASH, "101", "0", null, item(11L, 1, "0"))));
        assertThrows(IllegalArgumentException.class, () -> service.create(sale(PaymentMethod.CASH, "0", "0", null, item(11L, 1, "100.01"))));
        verify(invoices, never()).save(any());
    }

    @Test
    void cashPartIsPaidForCashZeroForCardsAndTypedForMixed() {
        service.create(sale(PaymentMethod.CASH, "0", "60", null, item(11L, 1, "0")));
        assertEquals(0, new BigDecimal("60").compareTo(saved().getCashAmount()));

        clearInvocations(invoices);
        service.create(sale(PaymentMethod.VISA, "0", "100", null, item(11L, 1, "0")));
        assertEquals(0, saved().getCashAmount().signum());

        clearInvocations(invoices);
        service.create(sale(PaymentMethod.MIXED, "0", "100", "30", item(11L, 1, "0")));
        assertEquals(0, new BigDecimal("30").compareTo(saved().getCashAmount()));

        assertThrows(IllegalArgumentException.class, () -> service.create(sale(PaymentMethod.MIXED, "0", "100", null, item(11L, 1, "0"))));
        assertThrows(IllegalArgumentException.class, () -> service.create(sale(PaymentMethod.MIXED, "0", "50", "60", item(11L, 1, "0"))));
    }

    @Test
    void employeesAreLimitedToTheDiscountInTheSettingsAdminsAreNot() {
        when(users.getCurrentUser()).thenReturn(employee);
        // 10% of 200 = 20: line discount 15 + invoice discount 5 is allowed, 21 is not.
        assertDoesNotThrow(() -> service.create(sale(PaymentMethod.CASH, "5", "0", null, item(11L, 2, "15"))));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(sale(PaymentMethod.CASH, "6", "0", null, item(11L, 2, "15"))));
        assertTrue(ex.getMessage().contains("المسموح"));

        when(users.getCurrentUser()).thenReturn(admin);
        assertDoesNotThrow(() -> service.create(sale(PaymentMethod.CASH, "100", "0", null, item(11L, 2, "50"))));
    }

    @Test
    void aHiddenCustomerWhoBuysAgainIsRestored() {
        Customer hidden = Customer.builder().id(6L).name("قديم").phone("011").active(false).build();
        when(customers.findByPhone("011")).thenReturn(Optional.of(hidden));
        service.create(new SalesInvoiceRequest(1L, new CustomerInvoiceRequest("قديم", "011", null), PaymentMethod.CASH,
                List.of(item(11L, 1, "0")), BigDecimal.ZERO, BigDecimal.ZERO, null, null));
        assertTrue(hidden.getActive());
    }

    @Test
    void onlyAdminsSellOnInstallmentsAndThePlanIsCreatedFromTheSale() {
        var plan = new Elramy.Group.MafroshartElramyz.installment.InstallmentDtos.InstallmentRequest(6, new BigDecimal("10"), null);
        SalesInvoiceRequest onInstallments = new SalesInvoiceRequest(1L, new CustomerInvoiceRequest("محمد", "010", null), PaymentMethod.CASH,
                List.of(item(11L, 1, "0")), BigDecimal.ZERO, new BigDecimal("40"), null, null, plan);

        when(users.getCurrentUser()).thenReturn(employee);
        assertThrows(Elramy.Group.MafroshartElramyz.exception.UnauthorizedException.class, () -> service.create(onInstallments));
        verifyNoInteractions(installments);

        when(users.getCurrentUser()).thenReturn(admin);
        service.create(onInstallments);
        verify(installments).createPlan(any(SalesInvoice.class), eq(plan), eq(admin));
    }
}
