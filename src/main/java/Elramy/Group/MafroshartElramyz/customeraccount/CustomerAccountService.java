package Elramy.Group.MafroshartElramyz.customeraccount;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.customeraccount.CustomerAccountDtos.*;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.exception.CustomerNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.Customer;
import Elramy.Group.MafroshartElramyz.models.SalesInvoice;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.CustomerRepository;
import Elramy.Group.MafroshartElramyz.repository.SalesInvoiceRepository;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturn;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnRepository;
import Elramy.Group.MafroshartElramyz.services.UserService;
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
 * What a customer owes, and the payments that settle it (تحصيل).
 *
 * balance = (invoice totals - paid at the sale) - later payments - return credits.
 * Payments are open to every role (taken at the counter); a cash payment
 * counts in that day's cash closing.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerAccountService {

    private final CustomerRepository customers;
    private final CustomerPaymentRepository payments;
    private final SalesInvoiceRepository invoices;
    private final SalesReturnRepository returns;
    private final BranchRepository branches;
    private final UserService userService;
    private final ActivityLogService activityLog;

    @Transactional(readOnly = true)
    public BigDecimal balance(Long customerId) {
        return invoices.sumUnpaidByCustomer(customerId)
                .subtract(payments.sumByCustomer(customerId))
                .subtract(returns.sumCreditByCustomer(customerId))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public PaymentResponse recordPayment(Long customerId, PaymentRequest request) {
        Customer customer = customers.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
        userService.validateBranchAccess(request.branchId());
        Branch branch = branches.findById(request.branchId())
                .orElseThrow(() -> new ResourceNotFoundException("الفرع غير موجود"));
        if (request.paymentMethod() == PaymentMethod.MIXED) {
            throw new IllegalArgumentException("اختر طريقة دفع واحدة للتحصيل.");
        }
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal owed = balance(customerId);
        if (owed.signum() <= 0) {
            throw new IllegalArgumentException("لا يوجد مبلغ مستحق على هذا العميل.");
        }
        if (amount.compareTo(owed) > 0) {
            throw new IllegalArgumentException("المبلغ (" + plain(amount) + ") أكبر من المستحق على العميل (" + plain(owed) + " ج.م).");
        }

        String prefix = "PAY-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        CustomerPayment saved = payments.save(CustomerPayment.builder()
                .paymentNumber(prefix + (payments.countByPaymentNumberStartingWith(prefix) + 1))
                .customer(customer)
                .branch(branch)
                .amount(amount)
                .paymentMethod(request.paymentMethod())
                .notes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim())
                .receivedBy(userService.getCurrentUser())
                .build());
        BigDecimal after = owed.subtract(amount);

        activityLog.record(ActivityEntity.CUSTOMER_PAYMENT, ActivityAction.CREATE, saved.getId(),
                saved.getPaymentNumber(), "تحصيل " + plain(amount) + " ج.م من العميل «" + customer.getName() + "»",
                Changes.of().set("طريقة الدفع", request.paymentMethod()).add("المستحق", owed, after).set("ملاحظات", saved.getNotes()));
        return toResponse(saved, after);
    }

    @Transactional(readOnly = true)
    public AccountResponse account(Long customerId) {
        Customer customer = customers.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
        List<StatementEntry> raw = new ArrayList<>();
        BigDecimal invoiced = BigDecimal.ZERO, paidAtSale = BigDecimal.ZERO, later = BigDecimal.ZERO, credits = BigDecimal.ZERO;

        for (SalesInvoice i : invoices.findByCustomerIdOrderByCreatedAtAsc(customerId)) {
            invoiced = invoiced.add(i.getTotal());
            paidAtSale = paidAtSale.add(i.getPaid());
            BigDecimal unpaid = i.getTotal().subtract(i.getPaid());
            raw.add(new StatementEntry(EntryType.INVOICE, i.getId(), i.getInvoiceNumber(), i.getCreatedAt(),
                    "فاتورة بإجمالي " + plain(i.getTotal()) + "، مدفوع " + plain(i.getPaid()), unpaid, null));
        }
        for (CustomerPayment p : payments.findByCustomerIdOrderByCreatedAtAsc(customerId)) {
            later = later.add(p.getAmount());
            raw.add(new StatementEntry(EntryType.PAYMENT, p.getId(), p.getPaymentNumber(), p.getCreatedAt(),
                    "تحصيل (" + p.getPaymentMethod() + ")", p.getAmount().negate(), null));
        }
        for (SalesReturn r : returns.findByCustomerIdOrderByCreatedAtAsc(customerId)) {
            credits = credits.add(r.getCreditAmount());
            raw.add(new StatementEntry(EntryType.RETURN, r.getId(), r.getReturnNumber(), r.getCreatedAt(),
                    "مرتجع من " + r.getInvoice().getInvoiceNumber() + " بقيمة " + plain(r.getTotal())
                            + (r.getCashRefund().signum() > 0 ? "، رُد نقداً " + plain(r.getCashRefund()) : ""),
                    r.getCreditAmount().negate(), null));
        }
        raw.sort(Comparator.comparing(StatementEntry::date, Comparator.nullsFirst(Comparator.naturalOrder())));

        List<StatementEntry> entries = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (StatementEntry e : raw) {
            running = running.add(e.amount());
            entries.add(new StatementEntry(e.type(), e.id(), e.number(), e.date(), e.description(), e.amount(), running));
        }
        return new AccountResponse(customer.getId(), customer.getName(), customer.getPhone(), running,
                invoiced, paidAtSale, later, credits, entries);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments() {
        return payments.findAllByOrderByCreatedAtDesc().stream().map(p -> toResponse(p, null)).toList();
    }

    private static PaymentResponse toResponse(CustomerPayment p, BigDecimal balanceAfter) {
        return new PaymentResponse(p.getId(), p.getPaymentNumber(), p.getCustomer().getId(), p.getCustomer().getName(),
                p.getCustomer().getPhone(), p.getBranch() != null ? p.getBranch().getId() : null,
                p.getBranch() != null ? p.getBranch().getName() : null, p.getAmount(), p.getPaymentMethod(), p.getNotes(),
                p.getReceivedBy() != null ? p.getReceivedBy().getFullName() : null, p.getCreatedAt(), balanceAfter);
    }

    static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
