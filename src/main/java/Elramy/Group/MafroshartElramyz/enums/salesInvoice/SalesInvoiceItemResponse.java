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

        BigDecimal total



        ) {
}