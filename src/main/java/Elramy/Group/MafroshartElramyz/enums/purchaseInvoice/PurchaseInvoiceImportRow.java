package Elramy.Group.MafroshartElramyz.enums.purchaseInvoice;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;

import java.math.BigDecimal;

public record PurchaseInvoiceImportRow(

        String invoiceNumber,

        Long branchId,

        String productCode,

        Integer quantity,

        BigDecimal unitPrice,

        BigDecimal discount,

        PaymentMethod paymentMethod,

        String notes

) {
}