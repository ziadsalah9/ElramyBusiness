package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface StockTransactionRepository
        extends JpaRepository<StockTransaction, Long> {

    boolean existsByVariantId(Long variantId);

    /** Last sale per product: [productId, lastSaleAt]. */
    @org.springframework.data.jpa.repository.Query("SELECT t.product.id, MAX(t.createdAt) FROM StockTransaction t"
            + " WHERE t.transactionType = Elramy.Group.MafroshartElramyz.enums.TransactionType.SALE GROUP BY t.product.id")
    List<Object[]> lastSalePerProduct();
}
