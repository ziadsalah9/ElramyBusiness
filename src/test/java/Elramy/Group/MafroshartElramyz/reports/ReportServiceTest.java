package Elramy.Group.MafroshartElramyz.reports;

import Elramy.Group.MafroshartElramyz.customeraccount.CustomerPayment;
import Elramy.Group.MafroshartElramyz.customeraccount.CustomerPaymentRepository;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.models.*;
import Elramy.Group.MafroshartElramyz.reports.ReportDtos.*;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import Elramy.Group.MafroshartElramyz.repository.PurchaseInvoiceRepository;
import Elramy.Group.MafroshartElramyz.repository.SalesInvoiceRepository;
import Elramy.Group.MafroshartElramyz.repository.StockTransactionRepository;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturn;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnItem;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnRepository;
import Elramy.Group.MafroshartElramyz.stocktake.StocktakeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Sales, discounts, returns, profit and cash for a range. */
class ReportServiceTest {

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void figuresAddUpAcrossSalesReturnsAndPayments() {
        SalesInvoiceRepository invoices = mock(SalesInvoiceRepository.class);
        SalesReturnRepository returns = mock(SalesReturnRepository.class);
        CustomerPaymentRepository payments = mock(CustomerPaymentRepository.class);
        PurchaseInvoiceRepository purchases = mock(PurchaseInvoiceRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        StocktakeRepository stocktakes = mock(StocktakeRepository.class);
        Elramy.Group.MafroshartElramyz.expense.ExpenseRepository expenses = mock(Elramy.Group.MafroshartElramyz.expense.ExpenseRepository.class);
        Elramy.Group.MafroshartElramyz.installment.InstallmentRepository plans = mock(Elramy.Group.MafroshartElramyz.installment.InstallmentRepository.class);
        when(plans.sumAllMarkup()).thenReturn(BigDecimal.ZERO);
        ReportService service = new ReportService(invoices, returns, payments, purchases, products,
                mock(StockTransactionRepository.class), stocktakes, expenses, plans);
        when(expenses.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(any(), any())).thenReturn(List.of(
                Elramy.Group.MafroshartElramyz.expense.Expense.builder().category(Elramy.Group.MafroshartElramyz.expense.ExpenseCategory.RENT)
                        .amount(new BigDecimal("12")).build()));

        LocalDate day = LocalDate.of(2026, 10, 9);
        LocalDateTime at = day.atTime(12, 0);
        Product quilt = Product.builder().id(1L).name("لحاف").code("PRD-1").purchasePrice(new BigDecimal("60")).active(true).build();
        // 2 x 100 with an invoice discount of 20 -> total 180 (every line at 90%). Paid 150, 100 of it cash (MIXED).
        SalesInvoice inv = SalesInvoice.builder().id(1L).total(new BigDecimal("180")).paid(new BigDecimal("150"))
                .cashAmount(new BigDecimal("100")).paymentMethod(PaymentMethod.MIXED).build();
        inv.setCreatedAt(at);
        inv.getItems().add(SalesInvoiceItem.builder().product(quilt).quantity(2).unitPrice(new BigDecimal("100"))
                .total(new BigDecimal("200")).build());
        when(invoices.findInRangeWithItems(any(), any())).thenReturn(List.of(inv));
        when(invoices.sumUnpaid()).thenReturn(new BigDecimal("30"));

        // One piece back at 90, refunded in cash.
        SalesReturn ret = SalesReturn.builder().total(new BigDecimal("90")).cashRefund(new BigDecimal("90")).creditAmount(BigDecimal.ZERO).build();
        ret.setCreatedAt(at);
        ret.getItems().add(SalesReturnItem.builder().product(quilt).quantity(1).total(new BigDecimal("90")).build());
        when(returns.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(List.of(ret));
        when(returns.sumAllCredits()).thenReturn(BigDecimal.ZERO);

        CustomerPayment pay = CustomerPayment.builder().amount(new BigDecimal("20")).paymentMethod(PaymentMethod.CASH).build();
        when(payments.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(List.of(pay));
        when(payments.sumAll()).thenReturn(new BigDecimal("20"));
        when(products.findAll()).thenReturn(List.of());

        Report r = service.build(day, day);
        Summary s = r.summary();

        eq("200", s.grossSales());
        eq("20", s.discounts());
        eq("90", s.returns());
        eq("90", s.netSales());          // 180 - 90
        eq("60", s.costOfSales());       // 2 x 60 sold - 1 x 60 back
        eq("30", s.grossProfit());       // (180 - 120) - (90 - 60)
        eq("30", s.cashIn());            // 100 cash at sale + 20 payment - 90 refund
        eq("10", s.outstandingBalances()); // 30 unpaid - 20 paid later
        eq("12", s.expenses());
        eq("18", s.netProfit());          // 30 gross profit - 12 rent
        assertEquals(1, r.expenses().size());
        assertEquals(1, r.topProducts().size());
        assertEquals(1, r.topProducts().get(0).quantity());
        assertEquals(1, r.days().size());
        eq("30", r.days().get(0).profit());
    }

    @Test
    void rangesAreValidated() {
        ReportService service = new ReportService(mock(SalesInvoiceRepository.class), mock(SalesReturnRepository.class),
                mock(CustomerPaymentRepository.class), mock(PurchaseInvoiceRepository.class), mock(ProductRepository.class),
                mock(StockTransactionRepository.class), mock(StocktakeRepository.class), mock(Elramy.Group.MafroshartElramyz.expense.ExpenseRepository.class),
                mock(Elramy.Group.MafroshartElramyz.installment.InstallmentRepository.class));
        assertThrows(IllegalArgumentException.class, () -> service.build(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.build(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)));
    }
}
