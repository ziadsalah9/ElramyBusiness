package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.models.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransactionRepository
        extends JpaRepository<StockTransaction, Long> {

    List<StockTransaction> findByProductStockIdOrderByCreatedAtDesc(
            Long productStockId
    );

    List<StockTransaction> findByProductStockProductIdOrderByCreatedAtDesc(
            Long productId
    );

    List<StockTransaction> findByProductStockBranchIdOrderByCreatedAtDesc(
            Long branchId
    );

    List<StockTransaction> findByTransactionTypeOrderByCreatedAtDesc(
            TransactionType transactionType
    );

    List<StockTransaction> findByReferenceId(
            Long referenceId
    );
}