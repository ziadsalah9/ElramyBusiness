package Elramy.Group.MafroshartElramyz.dto.product;

/** A variant found by its code or barcode, with its product. */
public record VariantLookupResponse(

        ProductResponse product,
        ProductVariantResponse variant

) {
}
