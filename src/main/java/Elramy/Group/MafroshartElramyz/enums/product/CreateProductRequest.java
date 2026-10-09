package Elramy.Group.MafroshartElramyz.enums.product;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(max = 200)
        String name,

        @Size(max = 100)
        String model,

        @NotNull(message = "Product type is required")
        ProductType itemType,

        @Min(value = 0, message = "Minimum quantity cannot be negative")
        @Schema(defaultValue = "0", description = "Low-stock alert threshold for the product total", example = "0")
        Integer minimumQuantity,

        /** Colour/size combinations. Empty or null = one variant with neither. */
        List<ProductVariantRequest> variants,

        /**
         * Total available quantity typed in the form (optional). The colours/
         * sizes may not add up to more; any remainder is kept on a
         * "no colour or size" row (ProductFieldRules.applyTotal).
         */
        @Min(value = 0, message = "إجمالي الكمية لا يمكن أن يكون سالباً")
        Integer totalQuantity

) {

    public CreateProductRequest(String name, String model, ProductType itemType, Integer minimumQuantity, List<ProductVariantRequest> variants) {
        this(name, model, itemType, minimumQuantity, variants, null);
    }
}
