package Elramy.Group.MafroshartElramyz.enums.product;

public record ProductVariantResponse(

        Long id,
        String code,
        String barcode,
        String color,
        String size,
        /** "أحمر / كبير" etc. */
        String label,
        Integer quantity,
        Boolean active

) {
}
