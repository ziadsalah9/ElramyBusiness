package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {


    Optional<ProductPrice> findByProductIdAndActiveTrue(Long productId);

    List<ProductPrice> findByProductIdOrderByCreatedAtDesc(Long productId);

}