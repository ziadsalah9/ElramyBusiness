package Elramy.Group.MafroshartElramyz.salesreturn;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class SalesReturnDtos {

    private SalesReturnDtos() {
    }

    /** CASH: the whole value is handed back. CREDIT: it first clears what the customer owes, the rest in cash. */
    public enum RefundMethod { CASH, CREDIT }

    public record ItemRequest(
            @NotNull(message = "بند الفاتورة مطلوب") Long invoiceItemId,
            @NotNull(message = "الكمية مطلوبة") @Min(value = 1, message = "الكمية يجب أن تكون 1 على الأقل") Integer quantity) {
    }

    public record ReturnRequest(
            @NotEmpty(message = "اختر صنفاً واحداً على الأقل للإرجاع") List<@Valid ItemRequest> items,
            @NotNull(message = "اختر طريقة رد المبلغ") RefundMethod refundMethod,
            @Size(max = 500, message = "السبب طويل جداً") String reason) {
    }

    public record ItemResponse(Long invoiceItemId, Long productId, String productName, String variantLabel,
                               int quantity, BigDecimal unitRefund, BigDecimal total) {
    }

    public record ReturnResponse(
            Long id, String returnNumber, Long invoiceId, String invoiceNumber,
            Long customerId, String customerName, String customerPhone, Long branchId,
            BigDecimal total, BigDecimal cashRefund, BigDecimal creditAmount, String reason,
            String createdByName, LocalDateTime createdAt, List<ItemResponse> items,
            /** Set when the return is part of an exchange. */
            Long exchangeInvoiceId, String exchangeInvoiceNumber) {
    }

    /** Return some items and take new ones in one step (ADMIN). */
    public record ExchangeRequest(
            @NotEmpty(message = "اختر صنفاً واحداً على الأقل للإرجاع") List<@Valid ItemRequest> returnItems,
            @NotEmpty(message = "اختر صنفاً جديداً واحداً على الأقل") List<@Valid NewItem> newItems,
            /** How the customer pays the difference when the new items cost more. */
            Elramy.Group.MafroshartElramyz.enums.PaymentMethod paymentMethod,
            /** Paid now towards the difference (0..difference); the rest is owed. */
            @jakarta.validation.constraints.DecimalMin(value = "0.0", message = "المبلغ لا يمكن أن يكون سالباً") java.math.BigDecimal paid,
            /** Cash part of `paid` when paymentMethod is MIXED. */
            @jakarta.validation.constraints.DecimalMin(value = "0.0", message = "المبلغ لا يمكن أن يكون سالباً") java.math.BigDecimal cashAmount,
            /** When the new items cost less: refund the rest in cash or credit it. */
            RefundMethod refundMethod,
            @Size(max = 500, message = "السبب طويل جداً") String reason) {
    }

    public record NewItem(
            @NotNull(message = "اختر اللون/المقاس") Long variantId,
            @NotNull(message = "الكمية مطلوبة") @Min(value = 1, message = "الكمية يجب أن تكون 1 على الأقل") Integer quantity) {
    }

    public record ExchangeResponse(ReturnResponse returned, Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceResponse newInvoice,
                                   java.math.BigDecimal returnValue, java.math.BigDecimal newTotal, java.math.BigDecimal paidNow,
                                   java.math.BigDecimal refundedCash, java.math.BigDecimal addedToBalance) {
    }
}
