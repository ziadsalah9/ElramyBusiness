package Elramy.Group.MafroshartElramyz.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "product_stocks",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"product_id","branch_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductStock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @Builder.Default
    private Integer minimumQuantity = 0;

}