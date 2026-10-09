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
import java.util.List;
import java.util.Optional;

/**
 * Installment sales (تقسيط).
 *
 * principal = invoice total - paid at the sale; markup = principal x %;
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
        BigDecimal markup = principal.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
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
                Changes.of().set("المتبقي", plain(principal)).set("نسبة الزيادة %", plain(pct)).set("قيمة الزيادة", plain(markup))
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
