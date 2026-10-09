package Elramy.Group.MafroshartElramyz.enums.purchaseInvoice;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseInvoiceItemRequest(

        /** Optional; the product is taken from the variant. */
        Long productId,

        /** The exact colour/size being bought (V11). */
        @NotNull(message = "Variant (colour/size) is required")
        Long variantId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.0", message = "Unit price cannot be negative")
        BigDecimal unitPrice,

        @DecimalMin(value = "0.0", message = "Item discount cannot be negative")
        BigDecimal discount

) {
}
