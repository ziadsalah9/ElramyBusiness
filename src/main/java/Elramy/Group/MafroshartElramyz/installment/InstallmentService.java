package Elramy.Group.MafroshartElramyz.installment;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.installment.InstallmentDtos.*;
import Elramy.Group.MafroshartElramyz.models.SalesInvoice;
import Elramy.Group.MafroshartElramyz.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Installment sales (تقسيط).
 *
 * principal = invoice total - paid at the sale; markup = INVOICE TOTAL x %
 * (the whole amount, down payment included);
 * total = principal + markup, split into equal months (the last one takes
 * the rounding). Customer payments go to the oldest unpaid installment
 * first; a return credited on the invoice shortens the plan from its end.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InstallmentService {

    private final InstallmentRepository plans;
    private final ActivityLogService activityLog;

    /** Creates the plan of a just-saved sale. */
    public InstallmentPlan createPlan(SalesInvoice invoice, InstallmentRequest request, User by) {
        if (invoice.getCustomer() == null) throw new IllegalArgumentException("البيع بالتقسيط يحتاج عميلاً.");
        BigDecimal principal = invoice.getTotal().subtract(invoice.getPaid()).setScale(2, RoundingMode.HALF_UP);
        if (principal.signum() <= 0) {
            throw new IllegalArgumentException("لا يوجد مبلغ متبقٍ للتقسيط: المقدم يساوي إجمالي الفاتورة.");
        }
        BigDecimal pct = request.markupPercent().setScale(2, RoundingMode.HALF_UP);
        // The % applies to the whole invoice, not only to what is left after the down payment.
        BigDecimal markup = invoice.getTotal().multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = principal.add(markup);
        LocalDate first = request.firstDueDate() != null ? request.firstDueDate() : LocalDate.now().plusMonths(1);
        if (first.isBefore(LocalDate.now())) throw new IllegalArgumentException("أول قسط لا يمكن أن يكون في الماضي.");

        InstallmentPlan plan = InstallmentPlan.builder().invoice(invoice).customer(invoice.getCustomer())
                .principal(principal).markupPercent(pct).markupAmount(markup).totalAmount(total)
                .months(request.months()).firstDueDate(first).createdBy(by).build();
        List<BigDecimal> amounts = split(total, request.months());
        for (int i = 0; i < amounts.size(); i++) {
            plan.getInstallments().add(Installment.builder().plan(plan).seq(i + 1)
                    .dueDate(first.plusMonths(i)).amount(amounts.get(i)).build());
        }
        InstallmentPlan saved = plans.save(plan);
        activityLog.record(ActivityEntity.INSTALLMENT, ActivityAction.CREATE, saved.getId(), invoice.getInvoiceNumber(),
                "تقسيط الفاتورة " + invoice.getInvoiceNumber() + " على " + request.months() + " شهر",
                Changes.of().set("إجمالي الفاتورة", plain(invoice.getTotal())).set("المتبقي", plain(principal)).set("نسبة الزيادة %", plain(pct)).set("قيمة الزيادة", plain(markup))
                        .set("إجمالي الأقساط", plain(total)).set("القسط", plain(amounts.get(0))).set("أول قسط", first));
        return saved;
    }

    /** Equal parts to the piaster; the last part absorbs the rounding. */
    static List<BigDecimal> split(BigDecimal total, int months) {
        BigDecimal each = total.divide(BigDecimal.valueOf(months), 2, RoundingMode.DOWN);
        List<BigDecimal> out = new ArrayList<>();
        for (int i = 0; i < months - 1; i++) out.add(each);
        out.add(total.subtract(each.multiply(BigDecimal.valueOf(months - 1))));
        return out;
    }

    /** A customer payment pays the oldest unpaid installments first; returns what was applied. */
    public BigDecimal allocatePayment(Long customerId, BigDecimal amount, LocalDateTime when) {
        BigDecimal left = amount;
        for (Installment i : plans.unpaidByCustomer(customerId)) {
            if (left.signum() <= 0) break;
            BigDecimal take = left.min(i.remaining());
            i.setPaidAmount(i.getPaidAmount().add(take));
            if (i.remaining().signum() == 0) i.setPaidAt(when);
            left = left.subtract(take);
        }
        return amount.subtract(left);
    }

    /** A return credited on an installment invoice removes that much from the last unpaid installments. */
    public void reduceForReturn(Long invoiceId, BigDecimal credit) {
        if (credit == null || credit.signum() <= 0) return;
        Optional<InstallmentPlan> plan = plans.findByInvoiceId(invoiceId);
        if (plan.isEmpty()) return;
        BigDecimal left = credit;
        List<Installment> list = new ArrayList<>(plan.get().getInstallments());
        for (int k = list.size() - 1; k >= 0 && left.signum() > 0; k--) {
            Installment i = list.get(k);
            BigDecimal take = left.min(i.remaining());
            i.setAmount(i.getAmount().subtract(take));
            left = left.subtract(take);
        }
    }

    @Transactional(readOnly = true)
    public Optional<PlanResponse> forInvoice(Long invoiceId) {
        return plans.findByInvoiceId(invoiceId).map(InstallmentService::toResponse);
    }

    /** Unpaid installments due within {@code days} from today, overdue ones first. */
    @Transactional(readOnly = true)
    public List<DueRow> due(int days) {
        LocalDate today = LocalDate.now();
        return plans.unpaidDueBy(today.plusDays(Math.max(0, Math.min(days, 365)))).stream().map(i -> {
            InstallmentPlan p = i.getPlan();
            long late = Math.max(0, ChronoUnit.DAYS.between(i.getDueDate(), today));
            return new DueRow(i.getId(), p.getId(), p.getInvoice().getId(), p.getInvoice().getInvoiceNumber(), p.getCustomer().getId(),
                    p.getCustomer().getName(), p.getCustomer().getPhone(), i.getSeq(), p.getMonths(), i.getDueDate(),
                    i.getAmount(), i.getPaidAmount(), i.remaining(), late);
        }).toList();
    }

    /**
     * Every customer who bought on installments, worst first: overdue
     * customers (longest late first), then those still paying (nearest due
     * first), then the ones who finished.
     */
    @Transactional(readOnly = true)
    public List<CustomerSummary> customers() {
        LocalDate today = LocalDate.now();
        Map<Long, List<InstallmentPlan>> byCustomer = new LinkedHashMap<>();
        for (InstallmentPlan p : plans.findAllWithSchedule()) {
            byCustomer.computeIfAbsent(p.getCustomer().getId(), k -> new ArrayList<>()).add(p);
        }
        List<CustomerSummary> out = new ArrayList<>();
        for (List<InstallmentPlan> list : byCustomer.values()) {
            out.add(summarize(list, today));
        }
        out.sort(Comparator
                .comparing((CustomerSummary s) -> s.overdueCount() > 0 ? 0 : s.remaining().signum() > 0 ? 1 : 2)
                .thenComparing(s -> -s.maxDaysLate())
                .thenComparing(s -> s.nextDueDate() == null ? LocalDate.MAX : s.nextDueDate())
                .thenComparing(CustomerSummary::customerName, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    /** One customer's plans with their schedules, newest first. */
    @Transactional(readOnly = true)
    public List<PlanResponse> forCustomer(Long customerId) {
        return plans.findByCustomerWithSchedule(customerId).stream()
                .sorted(Comparator.comparing((InstallmentPlan p) -> p.getInvoice().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(InstallmentService::toResponse)
                .toList();
    }

    static CustomerSummary summarize(List<InstallmentPlan> list, LocalDate today) {
        InstallmentPlan first = list.get(0);
        BigDecimal total = BigDecimal.ZERO, paid = BigDecimal.ZERO, remaining = BigDecimal.ZERO, overdue = BigDecimal.ZERO;
        int overdueCount = 0, openPlans = 0;
        long maxLate = 0;
        Installment next = null;
        LocalDate lastPlan = null;
        for (InstallmentPlan p : list) {
            total = total.add(p.getTotalAmount());
            boolean open = false;
            for (Installment i : p.getInstallments()) {
                paid = paid.add(i.getPaidAmount());
                BigDecimal left = i.remaining();
                if (left.signum() <= 0) continue;
                open = true;
                remaining = remaining.add(left);
                if (i.getDueDate().isBefore(today)) {
                    overdueCount++;
                    overdue = overdue.add(left);
                    maxLate = Math.max(maxLate, ChronoUnit.DAYS.between(i.getDueDate(), today));
                }
                if (next == null || i.getDueDate().isBefore(next.getDueDate())) next = i;
            }
            if (open) openPlans++;
            LocalDate created = p.getInvoice().getCreatedAt() == null ? p.getFirstDueDate() : p.getInvoice().getCreatedAt().toLocalDate();
            if (lastPlan == null || created.isAfter(lastPlan)) lastPlan = created;
        }
        return new CustomerSummary(first.getCustomer().getId(), first.getCustomer().getName(), first.getCustomer().getPhone(),
                list.size(), openPlans, total, paid, remaining, overdueCount, overdue, maxLate,
                next == null ? null : next.getDueDate(), next == null ? null : next.remaining(), lastPlan);
    }

    static PlanResponse toResponse(InstallmentPlan p) {
        LocalDate today = LocalDate.now();
        BigDecimal paid = BigDecimal.ZERO, remaining = BigDecimal.ZERO;
        List<InstallmentRow> rows = new ArrayList<>();
        for (Installment i : p.getInstallments()) {
            paid = paid.add(i.getPaidAmount());
            remaining = remaining.add(i.remaining());
            Status status = i.remaining().signum() == 0 ? Status.PAID
                    : i.getDueDate().isBefore(today) ? Status.OVERDUE
                    : i.getPaidAmount().signum() > 0 ? Status.PARTIAL : Status.DUE;
            long late = status == Status.OVERDUE ? ChronoUnit.DAYS.between(i.getDueDate(), today) : 0;
            rows.add(new InstallmentRow(i.getId(), i.getSeq(), i.getDueDate(), i.getAmount(), i.getPaidAmount(), i.remaining(), status, late));
        }
        return new PlanResponse(p.getId(), p.getInvoice().getId(), p.getInvoice().getInvoiceNumber(), p.getCustomer().getId(),
                p.getCustomer().getName(), p.getCustomer().getPhone(), p.getPrincipal(), p.getMarkupPercent(), p.getMarkupAmount(),
                p.getTotalAmount(), p.getMonths(), p.getFirstDueDate(), paid, remaining, rows);
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
