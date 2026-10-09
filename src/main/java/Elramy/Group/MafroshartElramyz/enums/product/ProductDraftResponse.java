package Elramy.Group.MafroshartElramyz.enums.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * An incomplete product. {@code missingFields} lists the required fields
 * still empty (Arabic labels); the draft can be completed when it is empty.
 */
public record ProductDraftResponse(

        Long id,
        String name,
        String itemType,
        String model,
        Integer minimumQuantity,
        BigDecimal purchasePrice,
        BigDecimal profitPercentage,
        BigDecimal sellingPrice,
        /** Colours/sizes with their quantities. */
        List<ProductDraftVariantDto> variants,
        List<String> missingFields,
        List<String> notes,
        String sourceFile,
        Integer sourceRow,
        LocalDateTime createdAt,
        Integer totalQuantity

) {
}
