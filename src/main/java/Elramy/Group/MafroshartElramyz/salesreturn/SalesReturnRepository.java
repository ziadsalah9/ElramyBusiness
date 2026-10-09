package Elramy.Group.MafroshartElramyz.salesreturn;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {

    @Query("SELECT COALESCE(SUM(r.creditAmount), 0) FROM SalesReturn r WHERE r.customer.id = :customerId")
    BigDecimal sumCreditByCustomer(@Param("customerId") Long customerId);

    /** Cash handed back over the counter in [from, to) - lowers the cash closing's expected cash. */
    @Query("SELECT COALESCE(SUM(r.cashRefund), 0) FROM SalesReturn r WHERE r.branch.id = :branchId"
            + " AND r.createdAt >= :from AND r.createdAt < :to")
    BigDecimal sumCashRefundByBranchBetween(@Param("branchId") Long branchId, @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    List<SalesReturn> findByInvoiceIdOrderByCreatedAtAsc(Long invoiceId);

    List<SalesReturn> findByCustomerIdOrderByCreatedAtAsc(Long customerId);

    List<SalesReturn> findAllByOrderByCreatedAtDesc();

    long countByReturnNumberStartingWith(String prefix);
}
