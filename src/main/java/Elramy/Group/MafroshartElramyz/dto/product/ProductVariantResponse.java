package Elramy.Group.MafroshartElramyz.dto.product;

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
