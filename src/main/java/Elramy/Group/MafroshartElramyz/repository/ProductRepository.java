package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCode(String code);

    boolean existsByBarcode(String barcode);

//    Optional<Product> findByCode(String code);
//
//    Optional<Product> findByBarcode(String barcode);

    @Query("""
        SELECT p FROM Product p
        WHERE
        LOWER(p.name) LIKE LOWER(CONCAT('%',:keyword,'%'))
        OR LOWER(p.code) LIKE LOWER(CONCAT('%',:keyword,'%'))
        OR LOWER(COALESCE(p.barcode,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))
        OR LOWER(COALESCE(p.model,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))
        OR LOWER(COALESCE(p.color,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))
        OR LOWER(COALESCE(p.size,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))
        """)
    List<Product> search(String keyword);
    Optional<Product> findByCode(String code);

}