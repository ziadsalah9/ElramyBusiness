package Elramy.Group.MafroshartElramyz.mapping;

import Elramy.Group.MafroshartElramyz.enums.customer.CustomerResponse;
import Elramy.Group.MafroshartElramyz.enums.salesInvoice.SalesInvoiceItemResponse;
import Elramy.Group.MafroshartElramyz.enums.salesInvoice.SalesInvoiceResponse;
import Elramy.Group.MafroshartElramyz.models.SalesInvoice;
import Elramy.Group.MafroshartElramyz.models.SalesInvoiceItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class SalesInvoiceMapper {

    private final CustomerMapper customerMapper;

    public SalesInvoiceResponse toResponse(
            SalesInvoice invoice) {

        CustomerResponse customer =
                invoice.getCustomer() != null
                        ? customerMapper.toResponse(
                        invoice.getCustomer()
                )
                        : null;

        BigDecimal remaining =
                invoice.getTotal()
                        .subtract(invoice.getPaid());

        return new SalesInvoiceResponse(

                // 1 - id
                invoice.getId(),

                // 2 - customer
                customer,

                // 3 - invoiceNumber
                invoice.getInvoiceNumber(),

                // 4 - branchId
                invoice.getBranch().getId(),

                // 5 - branchName
                invoice.getBranch().getName(),

                // 6 - cashierId
                invoice.getCashier() != null
                        ? invoice.getCashier().getId()
                        : null,

                // 7 - cashierName
                invoice.getCashier() != null
                        ? invoice.getCashier().getFullName()
                        : null,

                // 8 - items
                invoice.getItems()
                        .stream()
                        .map(this::toItemResponse)
                        .toList(),

                // 9 - discount
                invoice.getDiscount(),

                // 10 - total
                invoice.getTotal(),

                // 11 - paid
                invoice.getPaid(),

                // 12 - remaining
                remaining,

                // 13 - paymentMethod
                invoice.getPaymentMethod(),

                // 14 - notes
                invoice.getNotes(),

                // 15 - createdAt
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