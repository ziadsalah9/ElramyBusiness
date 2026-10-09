package Elramy.Group.MafroshartElramyz.salesreturn;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.customeraccount.CustomerAccountService;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.exception.SalesInvoiceNotFoundException;
import Elramy.Group.MafroshartElramyz.models.SalesInvoice;
import Elramy.Group.MafroshartElramyz.models.SalesInvoiceItem;
import Elramy.Group.MafroshartElramyz.repository.SalesInvoiceRepository;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnDtos.*;
import Elramy.Group.MafroshartElramyz.services.StockService;
import Elramy.Group.MafroshartElramyz.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Returns of sold items (مرتجعات), ADMIN-only.
 *
 * Each piece is refunded at what the customer really paid for it: the line
 * total (after its discount) per piece, scaled by the invoice discount.
 * The pieces go back to stock through the ledger (RETURN), and the value
 * is paid back in cash or credited against what the customer owes.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SalesReturnService {

    static final String REFERENCE_TYPE = "SALES_RETURN";

    private final SalesReturnRepository returns;
    private final SalesInvoiceRepository invoices;
    private final StockService stockService;
    private final CustomerAccountService accounts;
    private final UserService userService;
    private final ActivityLogService activityLog;

    public ReturnResponse create(Long invoiceId, ReturnRequest request) {
        SalesInvoice invoice = invoices.findById(invoiceId).orElseThrow(() -> new SalesInvoiceNotFoundException(invoiceId));

        Map<Long, SalesInvoiceItem> lines = new HashMap<>();
        for (SalesInvoiceItem i : invoice.getItems()) lines.put(i.getId(), i);

        // Invoice-level discount, spread over the lines in proportion.
        BigDecimal linesTotal = invoice.getItems().stream().map(SalesInvoiceItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ratio = linesTotal.signum() == 0 ? BigDecimal.ZERO
                : invoice.getTotal().divide(linesTotal, 10, RoundingMode.HALF_UP);

        String prefix = "RET-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        SalesReturn ret = SalesReturn.builder()
                .returnNumber(prefix + (returns.countByReturnNumberStartingWith(prefix) + 1))
                .invoice(invoice)
                .customer(invoice.getCustomer())
                .branch(invoice.getBranch())
                .reason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim())
                .createdBy(userService.getCurrentUser())
                .build();

        Set<Long> seen = new HashSet<>();
        BigDecimal value = BigDecimal.ZERO;
        Changes changes = Changes.of();
        for (ItemRequest r : request.items()) {
            if (!seen.add(r.invoiceItemId())) throw new IllegalArgumentException("نفس الصنف مكرر في طلب الإرجاع.");
            SalesInvoiceItem line = lines.get(r.invoiceItemId());
            if (line == null) throw new IllegalArgumentException("الصنف رقم " + r.invoiceItemId() + " ليس في هذه الفاتورة.");
            if (line.getVariant() == null) throw new IllegalArgumentException("لا يمكن إرجاع بند قديم بدون لون/مقاس.");
            int available = line.getQuantity() - line.getReturnedQuantity();
            if (r.quantity() > available) {
                throw new IllegalArgumentException(line.getProduct().getName() + " (" + line.getVariant().label() + "): المتاح للإرجاع "
                        + available + " فقط.");
            }
            BigDecimal unit = line.getTotal().divide(BigDecimal.valueOf(line.getQuantity()), 10, RoundingMode.HALF_UP)
                    .multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineValue = unit.multiply(BigDecimal.valueOf(r.quantity())).setScale(2, RoundingMode.HALF_UP);
            ret.getItems().add(SalesReturnItem.builder().salesReturn(ret).invoiceItem(line).product(line.getProduct())
                    .variant(line.getVariant()).quantity(r.quantity()).unitRefund(unit).total(lineValue).build());
            line.setReturnedQuantity(line.getReturnedQuantity() + r.quantity());
            value = value.add(lineValue);
            changes.set(line.getProduct().getName() + " (" + line.getVariant().label() + ")", r.quantity() + " × " + plain(unit));
        }

        // Rounding per piece may drift by a few piasters: never refund more than the invoice is worth.
        BigDecimal left = invoice.getTotal().subtract(invoice.getReturnedTotal());
        if (value.compareTo(left) > 0) value = left;
        if (value.signum() <= 0) throw new IllegalArgumentException("لا توجد قيمة متبقية للإرجاع في هذه الفاتورة.");

        BigDecimal credit = BigDecimal.ZERO;
        if (request.refundMethod() == RefundMethod.CREDIT && invoice.getCustomer() != null) {
            BigDecimal owed = accounts.balance(invoice.getCustomer().getId()).max(BigDecimal.ZERO);
            credit = value.min(owed);
        }
        ret.setTotal(value);
        ret.setCreditAmount(credit);
        ret.setCashRefund(value.subtract(credit));
        invoice.setReturnedTotal(invoice.getReturnedTotal().add(value));
        SalesReturn saved = returns.save(ret);

        // Back on the shelf, locked in variant order like a sale.
        for (SalesReturnItem item : saved.getItems().stream()
                .sorted(Comparator.comparing((SalesReturnItem i) -> i.getVariant().getId())).toList()) {
            stockService.increaseStock(item.getVariant().getId(), item.getQuantity(), TransactionType.RETURN,
                    item.getInvoiceItem().getUnitPrice(), saved.getId(), REFERENCE_TYPE, "مرتجع " + saved.getReturnNumber());
        }

        changes.set("القيمة", plain(value)).set("رُد نقداً", plain(saved.getCashRefund())).set("خُصم من المستحق", plain(credit))
                .set("السبب", saved.getReason());
        activityLog.record(ActivityEntity.SALES_RETURN, ActivityAction.CREATE, saved.getId(), saved.getReturnNumber(),
                "مرتجع " + saved.getReturnNumber() + " من الفاتورة " + invoice.getInvoiceNumber() + " بقيمة " + plain(value) + " ج.م",
                changes);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> forInvoice(Long invoiceId) {
        return returns.findByInvoiceIdOrderByCreatedAtAsc(invoiceId).stream().map(SalesReturnService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> all(java.time.LocalDate from, java.time.LocalDate to) {
        Elramy.Group.MafroshartElramyz.util.DateRange range = Elramy.Group.MafroshartElramyz.util.DateRange.of(from, to);
        return returns.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(range.from(), range.to())
                .stream().map(SalesReturnService::toResponse).toList();
    }

    static ReturnResponse toResponse(SalesReturn r) {
        List<ItemResponse> items = r.getItems().stream().map(i -> new ItemResponse(i.getInvoiceItem().getId(), i.getProduct().getId(),
                i.getProduct().getName(), i.getVariant() != null ? i.getVariant().label() : null, i.getQuantity(), i.getUnitRefund(),
                i.getTotal())).toList();
        return new ReturnResponse(r.getId(), r.getReturnNumber(), r.getInvoice().getId(), r.getInvoice().getInvoiceNumber(),
                r.getCustomer() != null ? r.getCustomer().getId() : null, r.getCustomer() != null ? r.getCustomer().getName() : null,
                r.getCustomer() != null ? r.getCustomer().getPhone() : null, r.getBranch() != null ? r.getBranch().getId() : null,
                r.getTotal(), r.getCashRefund(), r.getCreditAmount(), r.getReason(),
                r.getCreatedBy() != null ? r.getCreatedBy().getFullName() : null, r.getCreatedAt(), items);
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
