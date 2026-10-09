package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;

import java.math.BigDecimal;

/**
 * Stock movements per variant (colour × size, V11). Every movement locks
 * the variant row and writes one stock_transactions ledger row.
 */
public interface StockService {

    void increaseStock(
            Long variantId,
            Integer quantity,
            TransactionType transactionType,
            BigDecimal unitPrice,
            Long referenceId,
            String referenceType,
            String notes
    );

    /** Throws IllegalArgumentException when the variant has not enough stock. */
    void decreaseStock(
            Long variantId,
            Integer quantity,
            TransactionType transactionType,
            BigDecimal unitPrice,
            Long referenceId,
            String referenceType,
            String notes
    );
}
