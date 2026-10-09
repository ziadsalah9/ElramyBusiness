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
            String createdByName, LocalDateTime createdAt, List<ItemResponse> items) {
    }
}
