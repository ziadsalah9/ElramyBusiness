package Elramy.Group.MafroshartElramyz.installment;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class InstallmentDtos {

    private InstallmentDtos() {
    }

    /** Sent with a sale to sell on installments (ADMIN). firstDueDate: default one month after the sale. */
    public record InstallmentRequest(
            @NotNull(message = "عدد الشهور مطلوب") @Min(value = 1, message = "شهر واحد على الأقل") @Max(value = 60, message = "أقصى مدة 60 شهراً") Integer months,
            @NotNull(message = "نسبة الزيادة مطلوبة (0 مقبول)")
            @DecimalMin(value = "0", message = "النسبة لا يمكن أن تكون سالبة")
            @DecimalMax(value = "100", message = "النسبة لا تتجاوز 100%") BigDecimal markupPercent,
            LocalDate firstDueDate) {
    }

    public enum Status { PAID, PARTIAL, DUE, OVERDUE }

    public record InstallmentRow(Long id, int seq, LocalDate dueDate, BigDecimal amount, BigDecimal paidAmount,
                                 BigDecimal remaining, Status status, long daysLate) {
    }

    public record PlanResponse(Long id, Long invoiceId, String invoiceNumber, Long customerId, String customerName,
                               String customerPhone, BigDecimal principal, BigDecimal markupPercent, BigDecimal markupAmount,
                               BigDecimal totalAmount, int months, LocalDate firstDueDate, BigDecimal paid, BigDecimal remaining,
                               List<InstallmentRow> installments) {
    }

    /** An unpaid installment for the follow-up list. */
    public record DueRow(Long installmentId, Long planId, Long invoiceId, String invoiceNumber, Long customerId,
                         String customerName, String customerPhone, int seq, int months, LocalDate dueDate,
                         BigDecimal amount, BigDecimal paidAmount, BigDecimal remaining, long daysLate) {
    }
}
