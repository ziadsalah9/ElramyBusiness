package Elramy.Group.MafroshartElramyz.enums.product;

/** A variant found by its code or barcode, with its product. */
public record VariantLookupResponse(

        ProductResponse product,
        ProductVariantResponse variant

) {
}
