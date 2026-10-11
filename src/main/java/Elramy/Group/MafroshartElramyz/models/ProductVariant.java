package Elramy.Group.MafroshartElramyz.models;

import jakarta.persistence.*;
import lombok.*;

/**
 * One colour/size combination of a product (V11). Stock, code and barcode
 * live here; the price and the minimum quantity stay on the product.
 * Colour and size are each optional ("no colour" / "no size").
 */
@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 50)
    private String color;

    @Column(length = 50)
    private String size;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(unique = true)
    private String barcode;

    /** The 12-digit barcode used before V23 (kept for reference; scanning old labels goes through item_code_aliases, V27). */
    @Column(unique = true, length = 30)
    private String legacyBarcode;

    // Changed only through StockService, which locks the row and writes the ledger.
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** "أحمر / كبير", "أحمر", "كبير" or "بدون لون أو مقاس". */
    public String label() {
        boolean c = color != null && !color.isBlank();
        boolean s = size != null && !size.isBlank();
        if (c && s) return color + " / " + size;
        if (c) return color;
        if (s) return size;
        return "بدون لون أو مقاس";
    }
}
