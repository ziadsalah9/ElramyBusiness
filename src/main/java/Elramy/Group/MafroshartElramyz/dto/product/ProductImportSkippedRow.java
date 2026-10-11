package Elramy.Group.MafroshartElramyz.dto.product;

/** A row the import did not create or draft, and why (e.g. duplicate). */
public record ProductImportSkippedRow(

        int row,
        String name,
        String reason

) {
}
