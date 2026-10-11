package Elramy.Group.MafroshartElramyz.dto.salesInvoice;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SalesInvoiceItemRequest(

        /** Optional; the product is taken from the variant. */
        Long productId,

        /** The exact colour/size being sold (V11). */
        @NotNull(message = "Variant (colour/size) is required")
        Long variantId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        @DecimalMin(value = "0.0", message = "Item discount cannot be negative")
        java.math.BigDecimal discount

) {
}
