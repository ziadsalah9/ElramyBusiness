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

        BigDecimal total,

        // The colour/size sold or bought (V11); null on very old lines.
        Long variantId,
        String variantCode,
        String color,
        String size

) {
}