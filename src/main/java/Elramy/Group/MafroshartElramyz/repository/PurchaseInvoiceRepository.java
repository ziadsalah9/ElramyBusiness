package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.PurchaseInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseInvoiceRepository
        extends JpaRepository<PurchaseInvoice, Long> {

    boolean existsByInvoiceNumber(String invoiceNumber);

    Optional<PurchaseInvoice> findByInvoiceNumber(String invoiceNumber);
}