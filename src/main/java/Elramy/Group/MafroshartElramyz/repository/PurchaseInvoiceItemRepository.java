package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.PurchaseInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseInvoiceItemRepository
        extends JpaRepository<PurchaseInvoiceItem, Long> {

    List<PurchaseInvoiceItem> findByInvoiceId(Long invoiceId);

    List<PurchaseInvoiceItem> findByProductId(Long productId);
}