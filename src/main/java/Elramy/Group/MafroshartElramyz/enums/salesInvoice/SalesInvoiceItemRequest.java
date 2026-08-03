package Elramy.Group.MafroshartElramyz.enums.salesInvoice;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SalesInvoiceItemRequest(

        @NotNull
        Long productId,

        @NotNull
        @Min(1)
        Integer quantity,

        @Min(0)
        java.math.BigDecimal discount

) {
}