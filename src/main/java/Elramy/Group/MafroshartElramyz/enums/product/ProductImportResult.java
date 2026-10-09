package Elramy.Group.MafroshartElramyz.enums.product;

import java.util.List;

/**
 * Outcome of an Excel import: complete rows became products, incomplete
 * rows were saved as drafts ("منتجات غير مكتملة"), duplicates were skipped.
 */
public record ProductImportResult(

        /** New products created. */
        int created,
        int withPrice,
        /** Products created with an opening quantity > 0. */
        int withStock,
        /** Sum of the opening quantities of created products. */
        long totalQuantity,
        List<ProductResponse> products,

        /** New colours/sizes added to products that already existed. */
        int variantsAdded,

        int drafted,
        List<ProductDraftResponse> drafts,

        List<ProductImportSkippedRow> skipped,

        /** File-level notes, e.g. a column missing from the header row. */
        List<String> warnings

) {
}
