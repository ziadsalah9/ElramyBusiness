package Elramy.Group.MafroshartElramyz.stocktake;

import Elramy.Group.MafroshartElramyz.models.BaseEntity;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One counted colour/size. {@code systemQtyAtCount} is the stock when it
 * was (last) counted, so the difference stays right while sales go on.
 */
@Entity
@Table(name = "stocktake_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StocktakeLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stocktake_id")
    private Stocktake stocktake;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private Integer countedQty;

    @Column(nullable = false)
    private Integer systemQtyAtCount;

    private LocalDateTime countedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counted_by")
    private User countedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private StocktakeReason reason;

    @Column(length = 500)
    private String note;

    // Filled on approval: the prices used for the report and what was applied.
    @Column(precision = 18, scale = 2)
    private BigDecimal unitCost;
    @Column(precision = 18, scale = 2)
    private BigDecimal unitPrice;
    private Integer appliedVariance;

    /** A shortage that would have made stock negative was capped at 0. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean clamped = false;

    public int variance() {
        return countedQty - systemQtyAtCount;
    }
}
