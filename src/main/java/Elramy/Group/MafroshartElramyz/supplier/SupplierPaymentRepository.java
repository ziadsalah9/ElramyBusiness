package Elramy.Group.MafroshartElramyz.supplier;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SupplierPayment p WHERE p.supplier.id = :supplierId")
    BigDecimal sumBySupplier(@Param("supplierId") Long supplierId);

    /** Cash handed to suppliers in [from, to) - lowers the cash closing's expected cash. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SupplierPayment p WHERE p.branch.id = :branchId"
            + " AND p.paymentMethod = :method AND p.createdAt >= :from AND p.createdAt < :to")
    BigDecimal sumByBranchAndMethodBetween(@Param("branchId") Long branchId, @Param("method") PaymentMethod method,
                                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<SupplierPayment> findBySupplierIdOrderByCreatedAtAsc(Long supplierId);

    List<SupplierPayment> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    long countByPaymentNumberStartingWith(String prefix);
}
