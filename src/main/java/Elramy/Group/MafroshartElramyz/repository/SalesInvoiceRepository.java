package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.SalesInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalesInvoiceRepository
        extends JpaRepository<SalesInvoice, Long> {

    Optional<SalesInvoice> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    List<SalesInvoice> findByBranchIdOrderByCreatedAtDesc(Long branchId);

    List<SalesInvoice> findByCashierIdOrderByCreatedAtDesc(Long cashierId);

   List <SalesInvoice> findByCustomerId(Long id);

    boolean existsByCustomerId(Long customerId);

    List<SalesInvoice> findByBranchIdAndCreatedAtBetween(Long branchId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    /** What the customer still owes on their invoices, before later payments and returns. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(i.total - i.paid), 0) FROM SalesInvoice i WHERE i.customer.id = :customerId")
    java.math.BigDecimal sumUnpaidByCustomer(@org.springframework.data.repository.query.Param("customerId") Long customerId);

    /** Cash taken at the counter for sales in [from, to) - the cash closing's expected sales. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(i.cashAmount), 0) FROM SalesInvoice i WHERE i.branch.id = :branchId"
                    + " AND i.createdAt >= :from AND i.createdAt < :to")
    java.math.BigDecimal sumCashByBranchBetween(@org.springframework.data.repository.query.Param("branchId") Long branchId,
                                                @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                                                @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);

    List<SalesInvoice> findByCustomerIdOrderByCreatedAtAsc(Long customerId);
}
