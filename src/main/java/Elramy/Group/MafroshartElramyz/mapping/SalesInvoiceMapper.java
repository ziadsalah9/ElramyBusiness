package Elramy.Group.MafroshartElramyz.mapping;

import Elramy.Group.MafroshartElramyz.enums.salesInvoice.SalesInvoiceItemResponse;
import Elramy.Group.MafroshartElramyz.enums.salesInvoice.SalesInvoiceResponse;
import Elramy.Group.MafroshartElramyz.models.SalesInvoice;
import Elramy.Group.MafroshartElramyz.models.SalesInvoiceItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SalesInvoiceMapper {

    public SalesInvoiceResponse toResponse(
            SalesInvoice invoice) {

        BigDecimal remaining =
                invoice.getTotal()
                        .subtract(invoice.getPaid());

        return new SalesInvoiceResponse(

                invoice.getId(),

                invoice.getInvoiceNumber(),

                invoice.getBranch().getId(),
                invoice.getBranch().getName(),

                invoice.getCashier() != null
                        ? invoice.getCashier().getId()
                        : null,

                invoice.getCashier() != null
                        ? invoice.getCashier().getFullName()
                        : null,

                invoice.getItems()
                        .stream()
                        .map(this::toItemResponse)
                        .toList(),

                invoice.getDiscount(),

                invoice.getTotal(),

                invoice.getPaid(),

                remaining,

                invoice.getPaymentMethod(),

                invoice.getNotes(),

                invoice.getCreatedAt()
        );
    }


    private SalesInvoiceItemResponse toItemResponse(
            SalesInvoiceItem item) {

        return new SalesInvoiceItemResponse(

                item.getId(),

                item.getProduct().getId(),

                item.getProduct().getCode(),

                item.getProduct().getName(),

                item.getProduct().getModel(),

                item.getQuantity(),

                item.getUnitPrice(),

                item.getDiscount(),

                item.getTotal()
        );
    }
}