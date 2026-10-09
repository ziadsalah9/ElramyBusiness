package Elramy.Group.MafroshartElramyz.producttype;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemTypeRepository extends JpaRepository<ItemType, Long> {

    List<ItemType> findAllByOrderByIdAsc();

    Optional<ItemType> findByName(String name);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.itemType = :name")
    long countProducts(@Param("name") String name);

    @Query("SELECT COUNT(d) FROM ProductDraft d WHERE d.itemType = :name")
    long countDrafts(@Param("name") String name);

    /** Stocktake scopes store type names as text: keep them in step with a rename. */
    @Modifying
    @Query(value = "UPDATE stocktakes SET scope_types = array_to_string(array_replace(string_to_array(scope_types, ','), :old, :new), ',')"
            + " WHERE scope_types IS NOT NULL", nativeQuery = true)
    int renameInStocktakeScopes(@Param("old") String oldName, @Param("new") String newName);
}
