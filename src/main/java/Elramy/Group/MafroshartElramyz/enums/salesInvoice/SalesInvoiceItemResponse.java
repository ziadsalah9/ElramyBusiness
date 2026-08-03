package Elramy.Group.MafroshartElramyz.enums.salesInvoice;

import java.math.BigDecimal;

public record SalesInvoiceItemResponse(

        Long id,

        Long productId,

        String productCode,

        String productName,

        String model,

        Integer quantity,

        BigDecimal unitPrice,

        BigDecimal discount,

        BigDecimal total

) {
}