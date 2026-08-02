package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.enums.stock.StockResponse;
import Elramy.Group.MafroshartElramyz.enums.stock.StockTransactionResponse;

import java.math.BigDecimal;
import java.util.List;

public interface StockService {

    // =========================
    // Stock Queries
    // =========================

    StockResponse getById(Long id);

    StockResponse getByProductAndBranch(
            Long productId,
            Long branchId
    );

    List<StockResponse> getAll();

    List<StockResponse> getByProduct(Long productId);

    List<StockResponse> getByBranch(Long branchId);

    List<StockResponse> getLowStock();


    // =========================
    // Stock Operations
    // =========================

    void increaseStock(
            Long productId,
            Long branchId,
            Integer quantity,
            TransactionType transactionType,
            BigDecimal unitPrice,
            Long referenceId,
            String referenceType,
            String notes
    );

    void decreaseStock(
            Long productId,
            Long branchId,
            Integer quantity,
            TransactionType transactionType,
            BigDecimal unitPrice,
            Long referenceId,
            String referenceType,
            String notes
    );


    // =========================
    // Transactions History
    // =========================

    List<StockTransactionResponse> getTransactions(
            Long productStockId
    );

    List<StockTransactionResponse> getTransactionsByProduct(
            Long productId
    );

    List<StockTransactionResponse> getTransactionsByBranch(
            Long branchId
    );
}