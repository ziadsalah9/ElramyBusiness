package Elramy.Group.MafroshartElramyz.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findAllByOrderByNameAsc();

    @Query("SELECT COUNT(s) > 0 FROM Supplier s WHERE LOWER(TRIM(s.name)) = LOWER(TRIM(:name)) AND (:id IS NULL OR s.id <> :id)")
    boolean nameTaken(@Param("name") String name, @Param("id") Long id);
}
