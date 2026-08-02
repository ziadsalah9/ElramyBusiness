package Elramy.Group.MafroshartElramyz.enums.product;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateProductRequest(

        @NotBlank
        String name,

        String model,

        @NotBlank
        ProductType itemType,

        String color,

        String size,
//
//        @DecimalMin(value = "0.0")
//        BigDecimal purchasePrice,
//
//        @DecimalMin(value = "0.0")
//        BigDecimal profitPercentage,
//
//        @DecimalMin(value = "0.0")
//        BigDecimal sellingPrice,

        @Min(0)
        Integer minimumQuantity

) {
}