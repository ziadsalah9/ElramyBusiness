package Elramy.Group.MafroshartElramyz.salesreturn;

import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.customeraccount.CustomerAccountService;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.models.*;
import Elramy.Group.MafroshartElramyz.repository.SalesInvoiceRepository;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnDtos.*;
import Elramy.Group.MafroshartElramyz.services.StockService;
import Elramy.Group.MafroshartElramyz.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Refund what the customer really paid per piece; stock back; cash or credit. */
class SalesReturnServiceTest {

    private StockService stock;
    private CustomerAccountService accounts;
    private SalesReturnService service;
    private Elramy.Group.MafroshartElramyz.installment.InstallmentService installments;
    private SalesInvoice invoice;
    private SalesInvoiceItem quilts;   // 2 x 100
    private SalesInvoiceItem towel;    // 1 x 50

    @BeforeEach
    void setUp() {
        SalesReturnRepository returns = mock(SalesReturnRepository.class);
        SalesInvoiceRepository invoices = mock(SalesInvoiceRepository.class);
        stock = mock(StockService.class);
        accounts = mock(CustomerAccountService.class);
        installments = mock(Elramy.Group.MafroshartElramyz.installment.InstallmentService.class);
        service = new SalesReturnService(returns, invoices, stock, accounts, mock(UserService.class), mock(ActivityLogService.class), installments);

        Customer customer = Customer.builder().id(3L).name("محمد").phone("010").build();
        Product p = Product.builder().id(1L).name("لحاف").build();
        ProductVariant red = ProductVariant.builder().id(11L).product(p).color("أحمر").build();
        ProductVariant white = ProductVariant.builder().id(12L).product(p).color("أبيض").build();
        // Lines 200 + 50 = 250, invoice discount 25 -> total 225: every piece is refunded at 90%.
        invoice = SalesInvoice.builder().id(9L).invoiceNumber("INV-9").customer(customer).discount(new BigDecimal("25"))
                .total(new BigDecimal("225.00")).paid(new BigDecimal("125.00")).build();
        quilts = SalesInvoiceItem.builder().id(91L).invoice(invoice).product(p).variant(red).quantity(2)
                .unitPrice(new BigDecimal("100")).total(new BigDecimal("200.00")).build();
        towel = SalesInvoiceItem.builder().id(92L).invoice(invoice).product(p).variant(white).quantity(1)
                .unitPrice(new BigDecimal("50")).total(new BigDecimal("50.00")).build();
        invoice.getItems().addAll(List.of(quilts, towel));
        when(invoices.findById(9L)).thenReturn(Optional.of(invoice));
        when(returns.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ReturnResponse returnItems(RefundMethod method, ItemRequest... items) {
        return service.create(9L, new ReturnRequest(List.of(items), method, "عيب"));
    }

    @Test
    void piecesAreRefundedAfterTheInvoiceDiscountAndGoBackToStock() {
        ReturnResponse r = returnItems(RefundMethod.CASH, new ItemRequest(91L, 1));

        assertEquals(0, new BigDecimal("90.00").compareTo(r.total()));
        assertEquals(0, new BigDecimal("90.00").compareTo(r.cashRefund()));
        assertEquals(0, r.creditAmount().signum());
        assertEquals(1, quilts.getReturnedQuantity());
        assertEquals(0, new BigDecimal("90.00").compareTo(invoice.getReturnedTotal()));
        verify(stock).increaseStock(eq(11L), eq(1), eq(TransactionType.RETURN), any(), any(), eq("SALES_RETURN"), anyString());
    }

    @Test
    void creditFirstClearsWhatTheCustomerOwesThenTheRestIsCash() {
        when(accounts.balance(3L)).thenReturn(new BigDecimal("100.00"));
        ReturnResponse r = returnItems(RefundMethod.CREDIT, new ItemRequest(91L, 2), new ItemRequest(92L, 1));   // 180 + 45

        assertEquals(0, new BigDecimal("225.00").compareTo(r.total()));
        assertEquals(0, new BigDecimal("100.00").compareTo(r.creditAmount()));
        assertEquals(0, new BigDecimal("125.00").compareTo(r.cashRefund()));
        verify(installments).reduceForReturn(eq(9L), eq(new BigDecimal("100.00")));
    }

    @Test
    void moreThanWasSoldOrAlreadyReturnedIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> returnItems(RefundMethod.CASH, new ItemRequest(91L, 3)));
        returnItems(RefundMethod.CASH, new ItemRequest(91L, 2));
        assertThrows(IllegalArgumentException.class, () -> returnItems(RefundMethod.CASH, new ItemRequest(91L, 1)));
        assertThrows(IllegalArgumentException.class, () -> returnItems(RefundMethod.CASH, new ItemRequest(999L, 1)));
        assertThrows(IllegalArgumentException.class, () -> returnItems(RefundMethod.CASH, new ItemRequest(92L, 1), new ItemRequest(92L, 1)));
    }
}
