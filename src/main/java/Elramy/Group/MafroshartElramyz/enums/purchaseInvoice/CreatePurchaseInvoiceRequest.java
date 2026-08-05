package Elramy.Group.MafroshartElramyz.enums.purchaseInvoice;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreatePurchaseInvoiceRequest(

        @NotBlank
        String invoiceNumber,

        @NotNull
        Long branchId,

        @DecimalMin(value = "0.0")
        BigDecimal discount,

        @DecimalMin(value = "0.0")
        BigDecimal paid,

        @NotNull
        PaymentMethod paymentMethod,

        String notes,

        @NotEmpty
        @Valid
        List<PurchaseInvoiceItemRequest> items

) {
}