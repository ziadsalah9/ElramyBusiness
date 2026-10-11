package Elramy.Group.MafroshartElramyz.expense;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    public record ExpenseRequest(
            @NotNull(message = "الفرع مطلوب") Long branchId,
            @NotNull(message = "اختر نوع المصروف") ExpenseCategory category,
            @Size(max = 500, message = "الوصف طويل جداً") String description,
            @NotNull(message = "المبلغ مطلوب")
            @DecimalMin(value = "0.01", message = "المبلغ يجب أن يكون أكبر من صفر")
            @DecimalMax(value = "100000000", message = "المبلغ كبير جداً") BigDecimal amount,
            @NotNull(message = "طريقة الدفع مطلوبة") PaymentMethod paymentMethod,
            @NotNull(message = "التاريخ مطلوب") LocalDate expenseDate) {
    }

    public record ExpenseResponse(Long id, String expenseNumber, ExpenseCategory category, String description,
                                  BigDecimal amount, PaymentMethod paymentMethod, LocalDate expenseDate, Long branchId,
                                  String createdByName, LocalDateTime createdAt) {
    }
}
