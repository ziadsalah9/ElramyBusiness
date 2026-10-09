package Elramy.Group.MafroshartElramyz.models;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A product: identity, one price, and the low-stock threshold. Colour,
 * size, stock, variant code and barcode live on its variants (V11); the
 * legacy color/size/quantity/barcode columns stay in the table, unmapped.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String model;

    @Enumerated(EnumType.STRING)
    private ProductType itemType;

    // Current price, shared by every variant. All null until the product is
    // priced; a product without a selling price cannot be sold.
    @Column(precision = 18, scale = 2)
    private BigDecimal purchasePrice;

    @Column(precision = 7, scale = 2)
    private BigDecimal profitPercentage;

    @Column(precision = 18, scale = 2)
    private BigDecimal sellingPrice;

    private LocalDateTime priceUpdatedAt;

    // Low-stock alert threshold for the product's total (sum of variants).
    @Builder.Default
    private Integer minimumQuantity = 0;

    @Builder.Default
    private Boolean active = true;

    // Colour/size combinations (V11). Stock, code and barcode live there.
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    /** Active variants only. */
    public List<ProductVariant> activeVariants() {
        return variants.stream().filter(v -> !Boolean.FALSE.equals(v.getActive())).toList();
    }

    /** Total stock across the active variants. */
    public int totalQuantity() {
        return activeVariants().stream().mapToInt(v -> v.getQuantity() != null ? v.getQuantity() : 0).sum();
    }
}
