package Elramy.Group.MafroshartElramyz.enums;

public enum TransactionType {

    STOCK_IN,
    SALE,

    TRANSFER_IN,
    TRANSFER_OUT,

    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    // Sales return: pieces back on the shelf (V16; allowed by the DB check since V1).
    RETURN
}