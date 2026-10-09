package Elramy.Group.MafroshartElramyz.expense;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.expense.ExpenseDtos.*;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Shop expenses (ADMIN-only). Every change is in the activity log. */
@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final BranchRepository branches;
    private final UserService userService;
    private final ActivityLogService activityLog;

    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(LocalDate from, LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate t = to != null ? to : LocalDate.of(2200, 1, 1);
        if (t.isBefore(f)) throw new IllegalArgumentException("تاريخ النهاية قبل تاريخ البداية.");
        return expenses.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(f, t).stream().map(ExpenseService::toResponse).toList();
    }

    public ExpenseResponse create(ExpenseRequest r) {
        Branch branch = branch(r.branchId());
        check(r);
        String prefix = "EXP-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        Expense e = expenses.save(Expense.builder()
                .expenseNumber(prefix + (expenses.countByExpenseNumberStartingWith(prefix) + 1))
                .category(r.category()).description(blank(r.description()))
                .amount(r.amount().setScale(2, RoundingMode.HALF_UP)).paymentMethod(r.paymentMethod())
                .expenseDate(r.expenseDate()).branch(branch).createdBy(userService.getCurrentUser()).build());
        activityLog.record(ActivityEntity.EXPENSE, ActivityAction.CREATE, e.getId(), e.getExpenseNumber(),
                "مصروف " + e.getCategory() + " بقيمة " + e.getAmount().stripTrailingZeros().toPlainString() + " ج.م",
                Changes.of().set("التاريخ", e.getExpenseDate()).set("طريقة الدفع", e.getPaymentMethod()).set("الوصف", e.getDescription()));
        return toResponse(e);
    }

    public ExpenseResponse update(Long id, ExpenseRequest r) {
        Expense e = find(id);
        check(r);
        Changes changes = Changes.of().add("النوع", e.getCategory(), r.category()).add("الوصف", e.getDescription(), blank(r.description()))
                .add("المبلغ", e.getAmount(), r.amount()).add("طريقة الدفع", e.getPaymentMethod(), r.paymentMethod())
                .add("التاريخ", e.getExpenseDate(), r.expenseDate());
        e.setBranch(branch(r.branchId()));
        e.setCategory(r.category());
        e.setDescription(blank(r.description()));
        e.setAmount(r.amount().setScale(2, RoundingMode.HALF_UP));
        e.setPaymentMethod(r.paymentMethod());
        e.setExpenseDate(r.expenseDate());
        if (!changes.isEmpty()) {
            activityLog.record(ActivityEntity.EXPENSE, ActivityAction.UPDATE, e.getId(), e.getExpenseNumber(), "تعديل مصروف " + e.getExpenseNumber(), changes);
        }
        return toResponse(e);
    }

    public void delete(Long id) {
        Expense e = find(id);
        expenses.delete(e);
        activityLog.record(ActivityEntity.EXPENSE, ActivityAction.DELETE, id, e.getExpenseNumber(), "حذف مصروف " + e.getExpenseNumber(),
                Changes.of().add("المبلغ", e.getAmount(), null).add("النوع", e.getCategory(), null).add("التاريخ", e.getExpenseDate(), null));
    }

    private void check(ExpenseRequest r) {
        if (r.paymentMethod() == PaymentMethod.MIXED) throw new IllegalArgumentException("اختر طريقة دفع واحدة.");
        if (r.expenseDate().isAfter(LocalDate.now())) throw new IllegalArgumentException("لا يمكن تسجيل مصروف بتاريخ في المستقبل.");
    }

    private Branch branch(Long id) {
        userService.validateBranchAccess(id);
        return branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("الفرع غير موجود"));
    }

    private Expense find(Long id) {
        return expenses.findById(id).orElseThrow(() -> new ResourceNotFoundException("المصروف رقم " + id + " غير موجود."));
    }

    private static ExpenseResponse toResponse(Expense e) {
        return new ExpenseResponse(e.getId(), e.getExpenseNumber(), e.getCategory(), e.getDescription(), e.getAmount(),
                e.getPaymentMethod(), e.getExpenseDate(), e.getBranch() != null ? e.getBranch().getId() : null,
                e.getCreatedBy() != null ? e.getCreatedBy().getFullName() : null, e.getCreatedAt());
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
