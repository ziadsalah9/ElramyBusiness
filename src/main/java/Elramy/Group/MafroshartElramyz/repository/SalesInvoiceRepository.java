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
}