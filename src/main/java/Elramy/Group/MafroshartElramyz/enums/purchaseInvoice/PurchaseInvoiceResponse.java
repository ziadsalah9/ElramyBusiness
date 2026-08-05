package Elramy.Group.MafroshartElramyz.enums.purchaseInvoice;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseInvoiceResponse(

        Long id,

        String invoiceNumber,

        Long branchId,

        String branchName,

        Long createdById,

        String createdByName,

        BigDecimal discount,

        BigDecimal total,

        BigDecimal paid,

        BigDecimal remaining,

        PaymentMethod paymentMethod,

        String notes,

        List<PurchaseInvoiceItemResponse> items,

        LocalDateTime createdAt

) {
}