package Elramy.Group.MafroshartElramyz.dto.product;

/**
 * One validation problem in an uploaded product sheet.
 *
 * @param row    the Excel row number as the user sees it (header = 1)
 * @param column the column header, or null for a whole-row / file problem
 */
public record ProductImportRowError(

        int row,
        String column,
        String message

) {
}
