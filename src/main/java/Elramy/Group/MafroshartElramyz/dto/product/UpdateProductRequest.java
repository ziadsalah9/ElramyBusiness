package Elramy.Group.MafroshartElramyz.dto.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateProductRequest(

        @NotBlank
        @Size(max = 200)
        String name,

        @Size(max = 100)
        String model,

        @NotNull
        @jakarta.validation.constraints.NotBlank(message = "اختر نوع المنتج")
        String itemType,

        @Min(value = 0, message = "Minimum quantity cannot be negative")
        Integer minimumQuantity,

        /**
         * The full wanted list of colour/size combinations: entries with an
         * id update that variant, entries without one are added, variants
         * left out are removed (deactivated if they have stock or history).
         * Null leaves the variants unchanged.
         */
        List<ProductVariantRequest> variants,

        /**
         * Total available quantity typed in the form (optional). The colours/
         * sizes may not add up to more; any remainder is kept on a
         * "no colour or size" row (ProductFieldRules.applyTotal).
         */
        @Min(value = 0, message = "إجمالي الكمية لا يمكن أن يكون سالباً")
        Integer totalQuantity

) {

    public UpdateProductRequest(String name, String model, String itemType, Integer minimumQuantity, List<ProductVariantRequest> variants) {
        this(name, model, itemType, minimumQuantity, variants, null);
    }
}
