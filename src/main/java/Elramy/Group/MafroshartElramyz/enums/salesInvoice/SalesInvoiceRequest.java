package Elramy.Group.MafroshartElramyz.enums.salesInvoice;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record SalesInvoiceRequest(

        @NotNull
        Long branchId,

        @NotNull
        PaymentMethod paymentMethod,

        @Valid
        @NotEmpty
        List<SalesInvoiceItemRequest> items,

        @NotNull
        @DecimalMin("0.0")
        BigDecimal discount,

        @NotNull
        @DecimalMin("0.0")
        BigDecimal paid,

        String notes

) {
}