package Elramy.Group.MafroshartElramyz.dto.purchaseInvoice;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreatePurchaseInvoiceRequest(

        @NotBlank(message = "Invoice number is required")
        @jakarta.validation.constraints.Size(max = 50)
        String invoiceNumber,

        @NotNull(message = "Branch is required")
        Long branchId,

        @DecimalMin(value = "0.0", message = "Invoice discount cannot be negative")
        BigDecimal discount,

        @DecimalMin(value = "0.0", message = "Paid amount cannot be negative")
        BigDecimal paid,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @jakarta.validation.constraints.Size(max = 500)
        String notes,

        @NotEmpty(message = "At least one item is required")
        @Valid
        List<@Valid PurchaseInvoiceItemRequest> items

) {
}