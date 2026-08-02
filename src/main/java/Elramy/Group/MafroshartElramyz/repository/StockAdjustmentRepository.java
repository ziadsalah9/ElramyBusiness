package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.StockAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockAdjustmentRepository
        extends JpaRepository<StockAdjustment, Long> {

    List<StockAdjustment> findByProductStockIdOrderByCreatedAtDesc(
            Long productStockId
    );

    List<StockAdjustment> findByProductStockBranchIdOrderByCreatedAtDesc(
            Long branchId
    );

    List<StockAdjustment> findByCreatedByIdOrderByCreatedAtDesc(
            Long userId
    );
}