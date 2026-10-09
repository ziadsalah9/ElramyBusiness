package Elramy.Group.MafroshartElramyz.supplier;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    public record SupplierRequest(
            @NotBlank(message = "اسم المورد مطلوب") @Size(max = 200, message = "الاسم طويل جداً") String name,
            @Pattern(regexp = "^$|\\+?\\d{7,20}", message = "رقم الهاتف يجب أن يتكون من 7 إلى 20 رقماً") String phone,
            @Size(max = 500, message = "العنوان طويل جداً") String address,
            @Size(max = 500, message = "الملاحظات طويلة جداً") String notes) {
    }

    public record SupplierResponse(Long id, String name, String phone, String address, String notes, boolean active,
                                   BigDecimal balance, LocalDateTime createdAt) {
    }

    public record PaymentRequest(
            @NotNull(message = "الفرع مطلوب") Long branchId,
            @NotNull(message = "المبلغ مطلوب") @DecimalMin(value = "0.01", message = "المبلغ يجب أن يكون أكبر من صفر") BigDecimal amount,
            @NotNull(message = "طريقة الدفع مطلوبة") PaymentMethod paymentMethod,
            @Size(max = 500, message = "الملاحظات طويلة جداً") String notes) {
    }

    public record PaymentResponse(Long id, String paymentNumber, Long supplierId, String supplierName, Long branchId,
                                  BigDecimal amount, PaymentMethod paymentMethod, String notes, String paidByName,
                                  LocalDateTime createdAt, BigDecimal balanceAfter) {
    }

    public enum EntryType { PURCHASE, PAYMENT }

    /** {@code amount}: what it adds to (purchase) or takes off (payment) what we owe. */
    public record StatementEntry(EntryType type, Long id, String number, LocalDateTime date, String description,
                                 BigDecimal amount, BigDecimal balanceAfter) {
    }

    public record AccountResponse(Long supplierId, String supplierName, BigDecimal balance, BigDecimal purchasesTotal,
                                  BigDecimal paidAtPurchase, BigDecimal laterPayments, List<StatementEntry> entries) {
    }
}
