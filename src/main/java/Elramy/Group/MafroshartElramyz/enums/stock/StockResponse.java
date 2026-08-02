package Elramy.Group.MafroshartElramyz.enums.stock;

import java.math.BigDecimal;

public record StockResponse(

        Long id,

        Long productId,
        String productCode,
        String productName,

        Long branchId,
        String branchName,

        Integer quantity,
        Integer minimumQuantity,

        boolean lowStock

) {
}