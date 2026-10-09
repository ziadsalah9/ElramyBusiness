package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;


public interface StockTransactionRepository
        extends JpaRepository<StockTransaction, Long> {

    boolean existsByVariantId(Long variantId);
}