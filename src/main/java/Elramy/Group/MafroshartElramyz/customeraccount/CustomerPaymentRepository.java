package Elramy.Group.MafroshartElramyz.customeraccount;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, Long> {

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM CustomerPayment p WHERE p.customer.id = :customerId")
    BigDecimal sumByCustomer(@Param("customerId") Long customerId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM CustomerPayment p WHERE p.branch.id = :branchId"
            + " AND p.paymentMethod = :method AND p.createdAt >= :from AND p.createdAt < :to")
    BigDecimal sumByBranchAndMethodBetween(@Param("branchId") Long branchId, @Param("method") PaymentMethod method,
                                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<CustomerPayment> findByCustomerIdOrderByCreatedAtAsc(Long customerId);

    List<CustomerPayment> findAllByOrderByCreatedAtDesc();

    long countByPaymentNumberStartingWith(String prefix);

    List<CustomerPayment> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM CustomerPayment p")
    BigDecimal sumAll();
}
