package Elramy.Group.MafroshartElramyz.salesreturn;

import Elramy.Group.MafroshartElramyz.models.BaseEntity;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.models.SalesInvoiceItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Pieces of one invoice line that were returned, at what they really cost the customer. */
@Entity
@Table(name = "sales_return_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesReturnItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id")
    private SalesReturn salesReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_item_id")
    private SalesInvoiceItem invoiceItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    /** Price per piece after the line and invoice discounts. */
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal unitRefund;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal total;
}
