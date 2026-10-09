package Elramy.Group.MafroshartElramyz.enums.product;

/**
 * A colour/size row of a draft. In a request, {@code id} identifies an
 * existing row (null = new); quantity null = not entered yet.
 */
public record ProductDraftVariantDto(

        Long id,
        String color,
        String size,
        Integer quantity,
        Integer sourceRow

) {
}
