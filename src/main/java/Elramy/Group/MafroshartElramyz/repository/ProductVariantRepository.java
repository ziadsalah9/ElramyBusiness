package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.enums.ProductType;
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

    Optional<ProductVariant> findByLegacyBarcode(String legacyBarcode);

    /** What a scanner or a typed code finds: the code, the barcode, or an old (pre-V23) barcode. */
    default Optional<ProductVariant> findByScanCode(String code) {
        return findByCode(code).or(() -> findByBarcode(code)).or(() -> findByLegacyBarcode(code));
    }


    // What a stocktake counts: active colours/sizes of active products.
    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product p WHERE v.active = true AND p.active = true ORDER BY p.name, v.id")
    List<ProductVariant> findAllCountable();

    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product p WHERE v.active = true AND p.active = true"
            + " AND p.itemType IN :types ORDER BY p.name, v.id")
    List<ProductVariant> findCountableByTypes(@Param("types") Collection<ProductType> types);
}
