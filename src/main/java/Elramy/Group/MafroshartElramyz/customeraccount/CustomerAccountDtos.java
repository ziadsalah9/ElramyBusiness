package Elramy.Group.MafroshartElramyz.customeraccount;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Requests and responses of the customer account (balance, payments). */
public final class CustomerAccountDtos {

    private CustomerAccountDtos() {
    }

    public record PaymentRequest(
            @NotNull(message = "الفرع مطلوب") Long branchId,
            @NotNull(message = "المبلغ مطلوب") @DecimalMin(value = "0.01", message = "المبلغ يجب أن يكون أكبر من صفر") BigDecimal amount,
            @NotNull(message = "طريقة الدفع مطلوبة") PaymentMethod paymentMethod,
            @Size(max = 500, message = "الملاحظات طويلة جداً") String notes) {
    }

    public record PaymentResponse(
            Long id, String paymentNumber, Long customerId, String customerName, String customerPhone,
            Long branchId, String branchName, BigDecimal amount, PaymentMethod paymentMethod, String notes,
            String receivedByName, LocalDateTime createdAt, BigDecimal balanceAfter) {
    }

    public enum EntryType { INVOICE, PAYMENT, RETURN }

    /** One line of the statement; {@code amount} is what it adds to (or, negative, takes off) the balance. */
    public record StatementEntry(
            EntryType type, Long id, String number, LocalDateTime date, String description,
            BigDecimal amount, BigDecimal balanceAfter) {
    }

    public record AccountResponse(
            Long customerId, String customerName, String customerPhone,
            /** What the customer owes now (never negative in practice). */
            BigDecimal balance,
            BigDecimal invoicedTotal, BigDecimal paidAtSale, BigDecimal laterPayments, BigDecimal returnCredits,
            List<StatementEntry> entries) {
    }
}
