package Elramy.Group.MafroshartElramyz.enums.purchaseInvoice;

import java.math.BigDecimal;

public record PurchaseInvoiceItemResponse(

        Long id,

        Long productId,

        String productCode,

        String productName,

        Integer quantity,

        BigDecimal unitPrice,

        BigDecimal discount,

        BigDecimal total

) {
}