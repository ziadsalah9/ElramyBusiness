package Elramy.Group.MafroshartElramyz.enums.stock;

import java.time.LocalDateTime;

public record StockAdjustmentResponse(

        Long id,

        Long productStockId,

        Long productId,
        String productName,

        Long branchId,
        String branchName,

        Integer systemQuantity,
        Integer actualQuantity,
        Integer difference,

        String notes,

        Long createdById,
        String createdByName,

        LocalDateTime createdAt

) {
}