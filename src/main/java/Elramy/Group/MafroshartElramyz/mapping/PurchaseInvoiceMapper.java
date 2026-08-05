package Elramy.Group.MafroshartElramyz.mapping;


import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.PurchaseInvoiceItemResponse;
import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.PurchaseInvoiceResponse;
import Elramy.Group.MafroshartElramyz.models.PurchaseInvoice;
import Elramy.Group.MafroshartElramyz.models.PurchaseInvoiceItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PurchaseInvoiceMapper {

    public PurchaseInvoiceResponse toResponse(
            PurchaseInvoice invoice) {

        BigDecimal remaining =
                invoice.getTotal()
                        .subtract(invoice.getPaid());

        return new PurchaseInvoiceResponse(

                invoice.getId(),

                invoice.getInvoiceNumber(),

                invoice.getBranch().getId(),
                invoice.getBranch().getName(),

                invoice.getCreatedBy() != null
                        ? invoice.getCreatedBy().getId()
                        : null,

                invoice.getCreatedBy() != null
                        ? invoice.getCreatedBy().getFullName()
                        : null,

                invoice.getDiscount(),

                invoice.getTotal(),

                invoice.getPaid(),

                remaining,

                invoice.getPaymentMethod(),

                invoice.getNotes(),

                invoice.getItems()
                        .stream()
                        .map(this::toItemResponse)
                        .toList(),

                invoice.getCreatedAt()
        );
    }

    private PurchaseInvoiceItemResponse toItemResponse(
            PurchaseInvoiceItem item) {

        return new PurchaseInvoiceItemResponse(

                item.getId(),

                item.getProduct().getId(),

                item.getProduct().getCode(),

                item.getProduct().getName(),

                item.getQuantity(),

                item.getUnitPrice(),

                item.getDiscount(),

                item.getTotal()
        );
    }
}