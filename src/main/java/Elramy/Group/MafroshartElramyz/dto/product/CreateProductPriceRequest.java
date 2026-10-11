package Elramy.Group.MafroshartElramyz.dto.product;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record CreateProductPriceRequest (
        @DecimalMin(value = "0.0", message = "Purchase price cannot be negative")
        BigDecimal purchasePrice,

        @DecimalMin(value = "0.0", message = "Profit percentage cannot be negative")
        BigDecimal profitPercentage,

        @DecimalMin(value = "0.0", message = "Selling price cannot be negative")
        BigDecimal sellingPrice
        )
{
}
