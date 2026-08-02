package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductStockRepository
        extends JpaRepository<ProductStock, Long> {

    Optional<ProductStock> findByProductIdAndBranchId(
            Long productId,
            Long branchId
    );

    List<ProductStock> findByProductId(Long productId);

    List<ProductStock> findByBranchId(Long branchId);

    List<ProductStock> findByQuantityLessThanEqual(Integer quantity);

    boolean existsByProductIdAndBranchId(
            Long productId,
            Long branchId
    );

    @Query("""
    SELECT ps
    FROM ProductStock ps
    WHERE ps.quantity <= ps.minimumQuantity
""")
    List<ProductStock> findLowStock();
}