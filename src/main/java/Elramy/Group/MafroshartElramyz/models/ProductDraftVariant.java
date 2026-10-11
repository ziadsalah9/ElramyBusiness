package Elramy.Group.MafroshartElramyz.models;

import jakarta.persistence.*;
import lombok.*;

/** One colour/size of an incomplete product, with its (opening) quantity (V12). */
@Entity
@Table(name = "product_draft_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDraftVariant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    private ProductDraft draft;

    @Column(length = 50)
    private String color;

    @Column(length = 50)
    private String size;

    /** Null = not entered yet (the draft is incomplete). 0 is a valid quantity. */
    private Integer quantity;

    /** Excel row this colour/size came from, if imported. */
    private Integer sourceRow;
}
