package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.PurchaseInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseInvoiceRepository
        extends JpaRepository<PurchaseInvoice, Long> {

    boolean existsByInvoiceNumber(String invoiceNumber);

    Optional<PurchaseInvoice> findByInvoiceNumber(String invoiceNumber);

    java.util.List<PurchaseInvoice> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(java.time.LocalDateTime from, java.time.LocalDateTime to);

    java.util.List<PurchaseInvoice> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(java.time.LocalDateTime from, java.time.LocalDateTime to);

    /** What we still owe a supplier on purchases (before later payments). */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(i.total - i.paid), 0) FROM PurchaseInvoice i WHERE i.supplier.id = :supplierId")
    java.math.BigDecimal sumUnpaidBySupplier(@org.springframework.data.repository.query.Param("supplierId") Long supplierId);

    java.util.List<PurchaseInvoice> findBySupplierIdOrderByCreatedAtAsc(Long supplierId);
}
