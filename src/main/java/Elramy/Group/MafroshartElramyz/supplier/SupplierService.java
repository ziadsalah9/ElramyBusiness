package Elramy.Group.MafroshartElramyz.supplier;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.exception.ConflictException;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.PurchaseInvoice;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.PurchaseInvoiceRepository;
import Elramy.Group.MafroshartElramyz.services.UserService;
import Elramy.Group.MafroshartElramyz.supplier.SupplierDtos.*;
import Elramy.Group.MafroshartElramyz.util.DateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Suppliers and what we owe them (ADMIN-only).
 * balance = (purchase totals - paid at purchase) - later supplier payments.
 * A cash payment to a supplier comes out of that day's cash drawer.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SupplierService {

    private final SupplierRepository suppliers;
    private final SupplierPaymentRepository payments;
    private final PurchaseInvoiceRepository purchases;
    private final BranchRepository branches;
    private final UserService userService;
    private final ActivityLogService activityLog;

    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        return suppliers.findAllByOrderByNameAsc().stream().map(s -> toResponse(s, balance(s.getId()))).toList();
    }

    public SupplierResponse create(SupplierRequest r) {
        String name = r.name().trim();
        if (suppliers.nameTaken(name, null)) throw new ConflictException("يوجد مورد بنفس الاسم: " + name);
        Supplier s = suppliers.save(Supplier.builder().name(name).phone(blank(r.phone())).address(blank(r.address()))
                .notes(blank(r.notes())).active(true).build());
        activityLog.record(ActivityEntity.SUPPLIER, ActivityAction.CREATE, s.getId(), s.getName(), "إضافة مورد «" + s.getName() + "»",
                Changes.of().set("الهاتف", s.getPhone()).set("العنوان", s.getAddress()));
        return toResponse(s, BigDecimal.ZERO);
    }

    public SupplierResponse update(Long id, SupplierRequest r) {
        Supplier s = find(id);
        String name = r.name().trim();
        if (suppliers.nameTaken(name, id)) throw new ConflictException("يوجد مورد بنفس الاسم: " + name);
        Changes changes = Changes.of().add("الاسم", s.getName(), name).add("الهاتف", s.getPhone(), blank(r.phone()))
                .add("العنوان", s.getAddress(), blank(r.address())).add("ملاحظات", s.getNotes(), blank(r.notes()));
        s.setName(name);
        s.setPhone(blank(r.phone()));
        s.setAddress(blank(r.address()));
        s.setNotes(blank(r.notes()));
        if (!changes.isEmpty()) {
            activityLog.record(ActivityEntity.SUPPLIER, ActivityAction.UPDATE, s.getId(), s.getName(), "تعديل مورد «" + s.getName() + "»", changes);
        }
        return toResponse(s, balance(id));
    }

    public SupplierResponse toggleActive(Long id) {
        Supplier s = find(id);
        s.setActive(!Boolean.TRUE.equals(s.getActive()));
        activityLog.record(ActivityEntity.SUPPLIER, ActivityAction.STATUS, s.getId(), s.getName(),
                (s.getActive() ? "تفعيل" : "إيقاف") + " المورد «" + s.getName() + "»");
        return toResponse(s, balance(id));
    }

    /** An active supplier, for a new purchase. */
    @Transactional(readOnly = true)
    public Supplier activeSupplier(Long id) {
        Supplier s = find(id);
        if (!Boolean.TRUE.equals(s.getActive())) throw new IllegalArgumentException("المورد «" + s.getName() + "» موقوف.");
        return s;
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(Long supplierId) {
        return purchases.sumUnpaidBySupplier(supplierId).subtract(payments.sumBySupplier(supplierId)).setScale(2, RoundingMode.HALF_UP);
    }

    public PaymentResponse pay(Long supplierId, PaymentRequest r) {
        Supplier s = find(supplierId);
        userService.validateBranchAccess(r.branchId());
        Branch branch = branches.findById(r.branchId()).orElseThrow(() -> new ResourceNotFoundException("الفرع غير موجود"));
        if (r.paymentMethod() == PaymentMethod.MIXED) throw new IllegalArgumentException("اختر طريقة دفع واحدة.");
        BigDecimal amount = r.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal owed = balance(supplierId);
        if (owed.signum() <= 0) throw new IllegalArgumentException("لا يوجد مبلغ مستحق لهذا المورد.");
        if (amount.compareTo(owed) > 0) {
            throw new IllegalArgumentException("المبلغ (" + plain(amount) + ") أكبر من المستحق للمورد (" + plain(owed) + " ج.م).");
        }
        String prefix = "SPY-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        SupplierPayment p = payments.save(SupplierPayment.builder()
                .paymentNumber(prefix + (payments.countByPaymentNumberStartingWith(prefix) + 1))
                .supplier(s).branch(branch).amount(amount).paymentMethod(r.paymentMethod())
                .notes(blank(r.notes())).paidBy(userService.getCurrentUser()).build());
        BigDecimal after = owed.subtract(amount);
        activityLog.record(ActivityEntity.SUPPLIER, ActivityAction.CREATE, p.getId(), p.getPaymentNumber(),
                "دفع " + plain(amount) + " ج.م للمورد «" + s.getName() + "»",
                Changes.of().set("طريقة الدفع", r.paymentMethod()).add("المستحق للمورد", owed, after).set("ملاحظات", p.getNotes()));
        return toResponse(p, after);
    }

    @Transactional(readOnly = true)
    public AccountResponse account(Long supplierId) {
        Supplier s = find(supplierId);
        List<StatementEntry> raw = new ArrayList<>();
        BigDecimal bought = BigDecimal.ZERO, paidAtPurchase = BigDecimal.ZERO, later = BigDecimal.ZERO;
        for (PurchaseInvoice i : purchases.findBySupplierIdOrderByCreatedAtAsc(supplierId)) {
            bought = bought.add(i.getTotal());
            paidAtPurchase = paidAtPurchase.add(i.getPaid());
            raw.add(new StatementEntry(EntryType.PURCHASE, i.getId(), i.getInvoiceNumber(), i.getCreatedAt(),
                    "فاتورة شراء بإجمالي " + plain(i.getTotal()) + "، مدفوع " + plain(i.getPaid()), i.getTotal().subtract(i.getPaid()), null));
        }
        for (SupplierPayment p : payments.findBySupplierIdOrderByCreatedAtAsc(supplierId)) {
            later = later.add(p.getAmount());
            raw.add(new StatementEntry(EntryType.PAYMENT, p.getId(), p.getPaymentNumber(), p.getCreatedAt(),
                    "دفعة (" + p.getPaymentMethod() + ")", p.getAmount().negate(), null));
        }
        raw.sort(Comparator.comparing(StatementEntry::date, Comparator.nullsFirst(Comparator.naturalOrder())));
        List<StatementEntry> entries = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (StatementEntry e : raw) {
            running = running.add(e.amount());
            entries.add(new StatementEntry(e.type(), e.id(), e.number(), e.date(), e.description(), e.amount(), running));
        }
        return new AccountResponse(s.getId(), s.getName(), running, bought, paidAtPurchase, later, entries);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(LocalDate from, LocalDate to) {
        DateRange range = DateRange.of(from, to);
        return payments.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(range.from(), range.to())
                .stream().map(p -> toResponse(p, null)).toList();
    }

    private Supplier find(Long id) {
        return suppliers.findById(id).orElseThrow(() -> new ResourceNotFoundException("المورد رقم " + id + " غير موجود."));
    }

    private static SupplierResponse toResponse(Supplier s, BigDecimal balance) {
        return new SupplierResponse(s.getId(), s.getName(), s.getPhone(), s.getAddress(), s.getNotes(),
                Boolean.TRUE.equals(s.getActive()), balance, s.getCreatedAt());
    }

    private static PaymentResponse toResponse(SupplierPayment p, BigDecimal after) {
        return new PaymentResponse(p.getId(), p.getPaymentNumber(), p.getSupplier().getId(), p.getSupplier().getName(),
                p.getBranch() != null ? p.getBranch().getId() : null, p.getAmount(), p.getPaymentMethod(), p.getNotes(),
                p.getPaidBy() != null ? p.getPaidBy().getFullName() : null, p.getCreatedAt(), after);
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
