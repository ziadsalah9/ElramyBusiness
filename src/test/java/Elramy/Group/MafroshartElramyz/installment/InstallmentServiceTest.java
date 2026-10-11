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
    void theMarkupIsOnTheWholeInvoiceAndTheRestPlusMarkupIsSplitIntoMonths() {
        LocalDate first = LocalDate.now().plusDays(10);
        InstallmentPlan p = service.createPlan(invoice, new InstallmentRequest(3, new BigDecimal("10"), first), null);

        eq("1000", p.getPrincipal());                       // 1100 invoice - 100 down payment
        eq("110", p.getMarkupAmount());                     // 10% of the WHOLE invoice (1100), not of 1000
        eq("1110", p.getTotalAmount());
        assertEquals(3, p.getInstallments().size());
        eq("370", p.getInstallments().get(0).getAmount());
        eq("370", p.getInstallments().get(2).getAmount());
        assertEquals(first.plusMonths(2), p.getInstallments().get(2).getDueDate());
        eq("1110", p.getInstallments().stream().map(Installment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
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

    @Test
    void theLastMonthTakesTheRounding() {
        List<BigDecimal> parts = InstallmentService.split(new BigDecimal("1100"), 3);
        eq("366.66", parts.get(0));
        eq("366.68", parts.get(2));
    }

    /** A plan for {@code customer} whose installments are (due in N days from today, amount, paid). */
    private static InstallmentPlan plan(Customer customer, long id, Object[]... rows) {
        SalesInvoice sale = SalesInvoice.builder().id(id).invoiceNumber("INV-" + id).build();
        sale.setCreatedAt(LocalDateTime.now().minusMonths(2));
        InstallmentPlan p = InstallmentPlan.builder().id(id).customer(customer).months(rows.length)
                .invoice(sale).firstDueDate(LocalDate.now()).build();
        BigDecimal total = BigDecimal.ZERO;
        int seq = 1;
        for (Object[] r : rows) {
            BigDecimal amount = new BigDecimal((String) r[1]);
            total = total.add(amount);
            p.getInstallments().add(Installment.builder().plan(p).seq(seq++).dueDate(LocalDate.now().plusDays((Integer) r[0]))
                    .amount(amount).paidAmount(new BigDecimal((String) r[2])).build());
        }
        p.setTotalAmount(total);
        return p;
    }

    @Test
    void customersSummariseAllTheirPlansAndTheMostLateComeFirst() {
        Customer late = Customer.builder().id(1L).name("متأخر").phone("011").build();
        Customer paying = Customer.builder().id(2L).name("منتظم").phone("012").build();
        Customer done = Customer.builder().id(3L).name("خلّص").phone("013").build();
        when(plans.findAllWithSchedule()).thenReturn(List.of(
                plan(done, 1, new Object[]{-40, "100", "100"}),
                plan(paying, 2, new Object[]{-10, "100", "100"}, new Object[]{20, "100", "30"}),
                // two plans for the late customer: 15 and 5 days late, one partly paid
                plan(late, 3, new Object[]{-15, "200", "50"}, new Object[]{15, "200", "0"}),
                plan(late, 4, new Object[]{-5, "100", "0"})));

        List<CustomerSummary> list = service.customers();

        assertEquals(List.of("متأخر", "منتظم", "خلّص"), list.stream().map(CustomerSummary::customerName).toList());
        CustomerSummary l = list.get(0);
        assertEquals(2, l.plans());
        assertEquals(2, l.openPlans());
        eq("500", l.totalAmount());
        eq("50", l.paid());
        eq("450", l.remaining());
        assertEquals(2, l.overdueCount());
        eq("250", l.overdueAmount());                       // 150 left of the first + 100
        assertEquals(15, l.maxDaysLate());
        assertEquals(LocalDate.now().minusDays(15), l.nextDueDate());
        eq("150", l.nextDueAmount());

        CustomerSummary p = list.get(1);
        assertEquals(0, p.overdueCount());
        eq("70", p.remaining());
        assertEquals(LocalDate.now().plusDays(20), p.nextDueDate());

        CustomerSummary d = list.get(2);
        assertEquals(0, d.openPlans());
        eq("0", d.remaining());
        assertNull(d.nextDueDate());
    }
}
