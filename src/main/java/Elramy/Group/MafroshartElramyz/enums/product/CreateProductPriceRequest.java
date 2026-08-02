package Elramy.Group.MafroshartElramyz.enums.product;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record CreateProductPriceRequest (
        @DecimalMin(value = "0.0")
        BigDecimal purchasePrice,

        @DecimalMin(value = "0.0")
        BigDecimal profitPercentage,

        @DecimalMin(value = "0.0")
        BigDecimal sellingPrice
        )
{
}
