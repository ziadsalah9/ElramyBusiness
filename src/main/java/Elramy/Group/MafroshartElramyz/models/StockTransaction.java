package Elramy.Group.MafroshartElramyz.models;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "stock_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Movements reference the product (V8). The legacy product_stock_id
    // column is kept in the table, unmapped, as history.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // The exact colour/size that moved (V11).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false)
    private Integer quantity;

    private BigDecimal unitPrice;

    /*
     * رقم المرجع
     * مثال:
     * فاتورة بيع
     * فاتورة مرتجع
     * تحويل
     * جرد
     */
    private Long referenceId;
    private String referenceType;

    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

}