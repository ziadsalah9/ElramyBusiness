package Elramy.Group.MafroshartElramyz.models;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * An incomplete product from an Excel import ("منتجات غير مكتملة").
 *
 * Kept out of `products` on purpose, so it can never be sold, bought,
 * counted in stock or listed until it is completed. Every data field is
 * optional - a draft is by definition missing something.
 */
@Entity
@Table(name = "product_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDraft extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    private ProductType itemType;

    @Column(length = 100)
    private String model;

    private Integer minimumQuantity;

    /** Total available quantity (V15); colours/sizes may not exceed it. */
    private Integer totalQuantity;

    @Column(precision = 18, scale = 2)
    private BigDecimal purchasePrice;

    @Column(precision = 7, scale = 2)
    private BigDecimal profitPercentage;

    @Column(precision = 18, scale = 2)
    private BigDecimal sellingPrice;

    /** What the import cleared and why, one line per cell. */
    @Column(columnDefinition = "TEXT")
    private String importNotes;

    private String sourceFile;

    private Integer sourceRow;

    private Long createdBy;

    // Colours/sizes with their quantities (V12); like a product's variants.
    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private java.util.List<ProductDraftVariant> variants = new java.util.ArrayList<>();

    public ProductDraftVariant addVariant(String color, String size, Integer quantity, Integer sourceRow) {
        ProductDraftVariant v = ProductDraftVariant.builder()
                .draft(this).color(color).size(size).quantity(quantity).sourceRow(sourceRow).build();
        variants.add(v);
        return v;
    }
}
