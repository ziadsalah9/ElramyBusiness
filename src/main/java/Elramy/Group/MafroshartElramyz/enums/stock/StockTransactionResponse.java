package Elramy.Group.MafroshartElramyz.enums.stock;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockTransactionResponse(

        Long id,

        Long productStockId,

        Long productId,
        String productName,

        Long branchId,
        String branchName,

        TransactionType transactionType,

        Integer quantity,

        BigDecimal unitPrice,

        Long referenceId,

        String notes,

        Long createdById,
        String createdByName,

        LocalDateTime createdAt

) {
}