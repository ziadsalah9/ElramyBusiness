package Elramy.Group.MafroshartElramyz.dto.salesInvoice;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.dto.customer.CustomerInvoiceRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record SalesInvoiceRequest(

        @NotNull(message = "Branch is required")
        Long branchId,

        @Valid
        @NotNull(message = "بيانات العميل مطلوبة")
        CustomerInvoiceRequest customer,


        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @NotEmpty(message = "At least one item is required")
        List<@Valid SalesInvoiceItemRequest> items,

        @NotNull(message = "Invoice discount is required")
        @DecimalMin(value = "0.0", message = "Invoice discount cannot be negative")
        BigDecimal discount,

        @NotNull(message = "Paid amount is required")
        @DecimalMin(value = "0.0", message = "Paid amount cannot be negative")
        BigDecimal paid,

        @jakarta.validation.constraints.Size(max = 500)
        String notes,

        /** Cash part of {@code paid}; required for MIXED, ignored otherwise. */
        @DecimalMin(value = "0.0", message = "المبلغ النقدي لا يمكن أن يكون سالباً")
        BigDecimal cashAmount,

        /** Sell on installments (ADMIN): what is not paid now becomes a monthly plan. */
        @Valid
        Elramy.Group.MafroshartElramyz.installment.InstallmentDtos.InstallmentRequest installment

) {

    public SalesInvoiceRequest(Long branchId, CustomerInvoiceRequest customer, PaymentMethod paymentMethod,
                               List<SalesInvoiceItemRequest> items, BigDecimal discount, BigDecimal paid, String notes,
                               BigDecimal cashAmount) {
        this(branchId, customer, paymentMethod, items, discount, paid, notes, cashAmount, null);
    }
}