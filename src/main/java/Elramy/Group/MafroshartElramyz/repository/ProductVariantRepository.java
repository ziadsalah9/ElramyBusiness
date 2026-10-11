package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findByCode(String code);

    Optional<ProductVariant> findByBarcode(String barcode);

    boolean existsByCode(String code);

    /** A code or barcode the item had before (V27 aliases: PRD-…, 100001, 12-digit barcodes). */
    @Query(value = "SELECT v.* FROM product_variants v JOIN item_code_aliases a ON a.variant_id = v.id WHERE a.code = :code",
            nativeQuery = true)
    Optional<ProductVariant> findByAlias(@Param("code") String code);

    /**
     * What a scanner or a typed code finds: the current code/barcode
     * (typed in any case, O/I/L read as 0/1) or any older code or barcode,
     * so labels printed before still work.
     */
    default Optional<ProductVariant> findByScanCode(String code) {
        for (String c : Elramy.Group.MafroshartElramyz.services.ItemCodes.candidates(code)) {
            Optional<ProductVariant> v = findByCode(c).or(() -> findByBarcode(c));
            if (v.isPresent()) return v;
        }
        for (String c : Elramy.Group.MafroshartElramyz.services.ItemCodes.candidates(code)) {
            Optional<ProductVariant> v = findByAlias(c);
            if (v.isPresent()) return v;
        }
        return Optional.empty();
    }


    // What a stocktake counts: active colours/sizes of active products.
    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product p WHERE v.active = true AND p.active = true ORDER BY p.name, v.id")
    List<ProductVariant> findAllCountable();

    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product p WHERE v.active = true AND p.active = true"
            + " AND p.itemType IN :types ORDER BY p.name, v.id")
    List<ProductVariant> findCountableByTypes(@Param("types") Collection<String> types);
}
