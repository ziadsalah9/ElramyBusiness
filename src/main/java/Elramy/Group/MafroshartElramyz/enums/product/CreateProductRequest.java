package Elramy.Group.MafroshartElramyz.enums.product;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.With;

import java.math.BigDecimal;

public record CreateProductRequest(
//
//        @NotBlank(message = "Code is required")
//        String code,

        //String barcode,

        @NotBlank(message = "Product name is required")
        String name,

        String model,

        @NotNull
        ProductType itemType,


        String color,

        String size,

//    @DecimalMin(value = "0.0")
//       BigDecimal purchasePrice,
//
//        @DecimalMin(value = "0.0")
//        BigDecimal profitPercentage,
//
//        @DecimalMin(value = "0.0")
//        BigDecimal sellingPrice,

        @Min(0)
        @Schema(defaultValue = "0", description = "Minimum stock quantity alert threshold", example = "0")
        Integer minimumQuantity

) {

}