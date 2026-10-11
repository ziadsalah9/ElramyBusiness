package Elramy.Group.MafroshartElramyz.productcolor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductColorRepository extends JpaRepository<ProductColor, Long> {

    List<ProductColor> findAllByOrderByIdAsc();

    Optional<ProductColor> findByName(String name);

    @Query("SELECT COUNT(v) FROM ProductVariant v WHERE v.color = :name")
    long countVariants(@Param("name") String name);

    @Query("SELECT COUNT(v) FROM ProductDraftVariant v WHERE v.color = :name")
    long countDraftVariants(@Param("name") String name);
}
