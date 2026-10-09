package Elramy.Group.MafroshartElramyz.enums.salesInvoice;

import Elramy.Group.MafroshartElramyz.enums.customer.CustomerResponse;

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

        BigDecimal total,

        // The colour/size sold or bought (V11); null on very old lines.
        Long variantId,
        String variantCode,
        String color,
        String size



        ) {
}