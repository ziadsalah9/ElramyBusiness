package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.SalesInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesInvoiceItemRepository
        extends JpaRepository<SalesInvoiceItem, Long> {

    List<SalesInvoiceItem> findByProductIdOrderByCreatedAtDesc(
            Long productId
    );

    List<SalesInvoiceItem> findByInvoiceId(Long invoiceId);
}