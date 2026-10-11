package Elramy.Group.MafroshartElramyz.dto.product;

import java.math.BigDecimal;
import java.util.List;

/**
 * Edit of a draft. Every field is optional (null = leave empty); values
 * that ARE given are validated, because a person is typing them.
 * itemType / colour / size accept the stored value or the UI label.
 * {@code variants} is the full list of colours/sizes (null = unchanged).
 */
public record ProductDraftRequest(

        String name,
        String itemType,
        String model,
        Integer minimumQuantity,
        BigDecimal purchasePrice,
        BigDecimal profitPercentage,
        BigDecimal sellingPrice,
        List<ProductDraftVariantDto> variants,

        /** Total available quantity; required to complete the draft. */
        Integer totalQuantity

) {

    public ProductDraftRequest(String name, String itemType, String model, Integer minimumQuantity, BigDecimal purchasePrice,
                               BigDecimal profitPercentage, BigDecimal sellingPrice, List<ProductDraftVariantDto> variants) {
        this(name, itemType, model, minimumQuantity, purchasePrice, profitPercentage, sellingPrice, variants, null);
    }
}
