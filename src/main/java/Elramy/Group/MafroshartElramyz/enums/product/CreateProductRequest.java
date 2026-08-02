package Elramy.Group.MafroshartElramyz.enums.product;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductRequest(
//
//        @NotBlank(message = "Code is required")
//        String code,

        String barcode,

        @NotBlank(message = "Product name is required")
        String name,

        String model,

        @NotNull
        ProductType itemType,


        String color,

        String size,

    @DecimalMin(value = "0.0")
       BigDecimal purchasePrice,

        @DecimalMin(value = "0.0")
        BigDecimal profitPercentage,

        @DecimalMin(value = "0.0")
        BigDecimal sellingPrice,

        @Min(0)
        Integer minimumQuantity

) {
}