package Elramy.Group.MafroshartElramyz.stocktake;

/** Why a counted quantity differs from the system. */
public enum StocktakeReason {
    DAMAGED,        // تالف
    LOST,           // مفقود
    COUNT_ERROR,    // خطأ عد سابق
    UNRECORDED,     // زيادة غير مسجلة
    OTHER           // أخرى
}
