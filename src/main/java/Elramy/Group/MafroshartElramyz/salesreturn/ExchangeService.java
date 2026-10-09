package Elramy.Group.MafroshartElramyz.salesreturn;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.customeraccount.CustomerAccountService;
import Elramy.Group.MafroshartElramyz.dto.customer.CustomerInvoiceRequest;
import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceItemRequest;
import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceRequest;
import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceResponse;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.SalesInvoiceNotFoundException;
import Elramy.Group.MafroshartElramyz.models.Customer;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.models.SalesInvoice;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import Elramy.Group.MafroshartElramyz.repository.SalesInvoiceRepository;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnDtos.*;
import Elramy.Group.MafroshartElramyz.services.SalesInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Exchange (استبدال), ADMIN-only: a return and a new sale in one
 * transaction, using the same rules as each on its own.
 *
 * R = value of the returned pieces (what the customer paid), N = price of
 * the new pieces. The return's credit pays for the new sale:
 *   N >= R: credit R; the customer pays up to N - R now, the rest is owed.
 *   N <  R: credit N, the rest R - N is refunded in cash, or (CREDIT) also
 *           credited against anything else the customer owes.
 * The return is booked first, so the very same colour/size can be swapped.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExchangeService {

    private final SalesInvoiceRepository invoices;
    private final ProductVariantRepository variants;
    private final SalesReturnService returns;
    private final SalesInvoiceService sales;
    private final CustomerAccountService accounts;
    private final ActivityLogService activityLog;

    public ExchangeResponse exchange(Long invoiceId, ExchangeRequest r) {
        SalesInvoice original = invoices.findById(invoiceId).orElseThrow(() -> new SalesInvoiceNotFoundException(invoiceId));
        Customer customer = original.getCustomer();
        if (customer == null) throw new IllegalArgumentException("الاستبدال يحتاج فاتورة لها عميل.");

        BigDecimal value = returns.previewValue(original, r.returnItems());
        BigDecimal newTotal = BigDecimal.ZERO;
        for (NewItem item : r.newItems()) {
            ProductVariant v = variants.findById(item.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("اللون/المقاس رقم " + item.variantId() + " غير موجود."));
            if (v.getProduct().getSellingPrice() == null) {
                throw new IllegalArgumentException(v.getProduct().getName() + " بدون سعر بيع.");
            }
            newTotal = newTotal.add(v.getProduct().getSellingPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        newTotal = newTotal.setScale(2, RoundingMode.HALF_UP);

        BigDecimal difference = newTotal.subtract(value);          // > 0: customer pays more
        BigDecimal paid = r.paid() == null ? BigDecimal.ZERO : r.paid().setScale(2, RoundingMode.HALF_UP);
        if (difference.signum() <= 0) paid = BigDecimal.ZERO;
        if (paid.compareTo(difference.max(BigDecimal.ZERO)) > 0) {
            throw new IllegalArgumentException("المدفوع أكبر من الفرق المطلوب (" + plain(difference) + " ج.م).");
        }
        PaymentMethod method = r.paymentMethod() != null ? r.paymentMethod() : PaymentMethod.CASH;
        BigDecimal newUnpaid = newTotal.subtract(paid);
        BigDecimal otherOwed = r.refundMethod() == RefundMethod.CREDIT
                ? accounts.balance(customer.getId()).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal creditLimit = newUnpaid.add(otherOwed);

        SalesReturn ret = returns.doReturn(original, r.returnItems(),
                r.reason() != null && !r.reason().isBlank() ? r.reason() : "استبدال",
                v -> v.min(creditLimit), null);

        SalesInvoiceResponse created = sales.create(new SalesInvoiceRequest(
                original.getBranch().getId(),
                new CustomerInvoiceRequest(customer.getName(), customer.getPhone(), customer.getAddress()),
                method,
                r.newItems().stream().map(i -> new SalesInvoiceItemRequest(null, i.variantId(), i.quantity(), BigDecimal.ZERO)).toList(),
                BigDecimal.ZERO, paid, "استبدال من الفاتورة " + original.getInvoiceNumber(), r.cashAmount()));
        SalesInvoice newInvoice = invoices.findById(created.id()).orElseThrow();
        ret.setExchangeInvoice(newInvoice);

        BigDecimal addedToBalance = newUnpaid.subtract(ret.getCreditAmount());
        activityLog.record(ActivityEntity.SALES_RETURN, ActivityAction.CREATE, ret.getId(), ret.getReturnNumber(),
                "استبدال: " + ret.getReturnNumber() + " من " + original.getInvoiceNumber() + " مقابل " + newInvoice.getInvoiceNumber(),
                Changes.of().set("قيمة المرتجع", plain(ret.getTotal())).set("قيمة الجديد", plain(newTotal))
                        .set("مدفوع الآن", plain(paid)).set("رُد نقداً", plain(ret.getCashRefund())).set("على الحساب", plain(addedToBalance)));
        return new ExchangeResponse(SalesReturnService.toResponse(ret), created, ret.getTotal(), newTotal, paid,
                ret.getCashRefund(), addedToBalance);
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
