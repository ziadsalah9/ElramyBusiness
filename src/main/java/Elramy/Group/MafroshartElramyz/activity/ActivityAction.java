package Elramy.Group.MafroshartElramyz.activity;

public enum ActivityAction {
    CREATE,
    UPDATE,
    DELETE,
    /** Deactivate / activate / restore and similar state changes. */
    STATUS,
    /** Price change. */
    PRICE,
    /** Stock quantity set from the product form. */
    STOCK,
    /** Bulk Excel import. */
    IMPORT,
    /** A draft added to the system as a product. */
    COMPLETE
}
