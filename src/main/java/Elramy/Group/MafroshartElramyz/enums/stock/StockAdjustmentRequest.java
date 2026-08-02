package Elramy.Group.MafroshartElramyz.enums.stock;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockAdjustmentRequest(

        @NotNull
        Long productStockId,

        @NotNull
        @Min(0)
        Integer actualQuantity,

        String notes

) {
}