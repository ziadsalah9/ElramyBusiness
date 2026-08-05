package Elramy.Group.MafroshartElramyz.enums.purchaseInvoice;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseInvoiceItemRequest(

        @NotNull
        Long productId,

        @NotNull
        @Min(1)
        Integer quantity,

        @NotNull
        @DecimalMin(value = "0.0")
        BigDecimal unitPrice,

        @DecimalMin(value = "0.0")
        BigDecimal discount

) {
}