package Elramy.Group.MafroshartElramyz.installment;

import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.installment.InstallmentDtos.*;
import Elramy.Group.MafroshartElramyz.models.Customer;
import Elramy.Group.MafroshartElramyz.models.SalesInvoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Plan amounts and dates, payments oldest-first, returns shorten the plan. */
class InstallmentServiceTest {

    private InstallmentRepository plans;
    private InstallmentService service;
    private SalesInvoice invoice;

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @BeforeEach
    void setUp() {
        plans = mock(InstallmentRepository.class);
        when(plans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new InstallmentService(plans, mock(ActivityLogService.class));
        invoice = SalesInvoice.builder().id(9L).invoiceNumber("INV-9").total(new BigDecimal("1100")).paid(new BigDecimal("100"))
                .customer(Customer.builder().id(3L).name("محمد").phone("010").build()).build();
    }

    @Test
    void theRestPlusMarkupIsSplitIntoMonthsTheLastTakesTheRounding() {
        LocalDate first = LocalDate.now().plusDays(10);
        InstallmentPlan p = service.createPlan(invoice, new InstallmentRequest(3, new BigDecimal("10"), first), null);

        eq("1000", p.getPrincipal());
        eq("100", p.getMarkupAmount());
        eq("1100", p.getTotalAmount());
        assertEquals(3, p.getInstallments().size());
        eq("366.66", p.getInstallments().get(0).getAmount());
        eq("366.68", p.getInstallments().get(2).getAmount());   // 1100 - 2 x 366.66
        assertEquals(first.plusMonths(2), p.getInstallments().get(2).getDueDate());
        eq("1100", p.getInstallments().stream().map(Installment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void nothingLeftToSplitOrAPastFirstDateIsRejected() {
        invoice.setPaid(new BigDecimal("1100"));
        assertThrows(IllegalArgumentException.class, () -> service.createPlan(invoice, new InstallmentRequest(3, BigDecimal.ZERO, null), null));
        invoice.setPaid(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> service.createPlan(invoice, new InstallmentRequest(3, BigDecimal.ZERO, LocalDate.now().minusDays(1)), null));
    }

    @Test
    void paymentsSettleTheOldestInstallmentFirst() {
        Installment a = Installment.builder().seq(1).dueDate(LocalDate.now().minusDays(5)).amount(new BigDecimal("300")).build();
        Installment b = Installment.builder().seq(2).dueDate(LocalDate.now().plusDays(25)).amount(new BigDecimal("300")).build();
        when(plans.unpaidByCustomer(3L)).thenReturn(List.of(a, b));

        eq("450", service.allocatePayment(3L, new BigDecimal("450"), LocalDateTime.now()));
        eq("300", a.getPaidAmount());
        assertNotNull(a.getPaidAt());
        eq("150", b.getPaidAmount());
        assertNull(b.getPaidAt());
    }

    @Test
    void aReturnCreditShortensThePlanFromTheEnd() {
        InstallmentPlan plan = service.createPlan(invoice, new InstallmentRequest(3, BigDecimal.ZERO, null), null);   // 333.33 x2 + 333.34
        plan.getInstallments().get(0).setPaidAmount(new BigDecimal("333.33"));
        when(plans.findByInvoiceId(9L)).thenReturn(Optional.of(plan));

        service.reduceForReturn(9L, new BigDecimal("400"));

        eq("333.33", plan.getInstallments().get(0).getAmount());   // paid one untouched
        eq("266.67", plan.getInstallments().get(1).getAmount());   // 333.33 - 66.66
        eq("0", plan.getInstallments().get(2).getAmount());        // 333.34 removed
    }

    @Test
    void overdueStatusInTheSchedule() {
        InstallmentPlan plan = service.createPlan(invoice, new InstallmentRequest(2, BigDecimal.ZERO, null), null);
        plan.getInstallments().get(0).setDueDate(LocalDate.now().minusDays(3));
        PlanResponse r = InstallmentService.toResponse(plan);
        assertEquals(Status.OVERDUE, r.installments().get(0).status());
        assertEquals(3, r.installments().get(0).daysLate());
        assertEquals(Status.DUE, r.installments().get(1).status());
    }
}
