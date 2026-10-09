package Elramy.Group.MafroshartElramyz.enums.product;

import java.util.List;

public record ProductResponse(

        Long id,

        String code,

        String name,

        String model,

        String itemType,

        /** One price for every variant; null until priced. */
        ProductPriceResponse currentPrice,

        Integer minimumQuantity,

        Boolean active,

        /** Total stock across the active variants. */
        Integer quantity,

        /** quantity <= minimumQuantity (on the product total). */
        Boolean lowStock,

        /** Active variants (colour × size), each with its own stock and code. */
        List<ProductVariantResponse> variants

) {
}
