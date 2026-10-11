package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCode(String code);


//    Optional<Product> findByCode(String code);
//
//    Optional<Product> findByBarcode(String barcode);

    // Matches the product or any of its variants (code, barcode, colour, size).
    @Query("""
        SELECT DISTINCT p FROM Product p
        WHERE
        LOWER(p.name) LIKE LOWER(CONCAT('%',:keyword,'%'))
        OR LOWER(p.code) LIKE LOWER(CONCAT('%',:keyword,'%'))
        OR LOWER(COALESCE(p.model,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))
        OR EXISTS (
            SELECT v FROM ProductVariant v WHERE v.product = p AND (
                LOWER(v.code) LIKE LOWER(CONCAT('%',:keyword,'%'))
                OR LOWER(COALESCE(v.barcode,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))
                OR LOWER(COALESCE(v.color,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))
                OR LOWER(COALESCE(v.size,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))))
        """)
    List<Product> search(@Param("keyword") String keyword);
    Optional<Product> findByCode(String code);

    /** A code the product had before (V27 aliases, e.g. PRD-7AF6C483). */
    @Query(value = "SELECT p.* FROM products p JOIN item_code_aliases a ON a.product_id = p.id WHERE a.code = :code",
            nativeQuery = true)
    Optional<Product> findByAlias(@Param("code") String code);

    /** A product by its code, typed in any case, or by a code it had before. */
    default Optional<Product> findByAnyCode(String code) {
        for (String c : Elramy.Group.MafroshartElramyz.services.ItemCodes.candidates(code)) {
            Optional<Product> p = findByCode(c);
            if (p.isPresent()) return p;
        }
        for (String c : Elramy.Group.MafroshartElramyz.services.ItemCodes.candidates(code)) {
            Optional<Product> p = findByAlias(c);
            if (p.isPresent()) return p;
        }
        return Optional.empty();
    }


    // Low stock is judged on the product total across its active variants.
    // Soft-deleted products are excluded, or they would warn forever.
    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true
          AND (SELECT COALESCE(SUM(v.quantity), 0) FROM ProductVariant v
                WHERE v.product = p AND v.active = true) <= p.minimumQuantity
        ORDER BY p.name
        """)
    List<Product> findLowStock();

    long countByActiveTrue();
}
