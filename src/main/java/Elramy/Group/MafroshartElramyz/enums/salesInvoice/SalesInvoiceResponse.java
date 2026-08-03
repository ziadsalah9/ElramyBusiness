package Elramy.Group.MafroshartElramyz.enums.salesInvoice;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SalesInvoiceResponse(

        Long id,

        String invoiceNumber,

        Long branchId,

        String branchName,

        Long cashierId,

        String cashierName,

        List<SalesInvoiceItemResponse> items,

        BigDecimal discount,

        BigDecimal total,

        BigDecimal paid,

        BigDecimal remaining,

        PaymentMethod paymentMethod,

        String notes,

        LocalDateTime createdAt

) {
}