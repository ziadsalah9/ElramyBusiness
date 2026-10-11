package Elramy.Group.MafroshartElramyz.producttype;

import Elramy.Group.MafroshartElramyz.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** A product type (لحاف، بطانيه، ...), managed by admins (V24). Products reference it by name. */
@Entity
@Table(name = "product_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** Hidden types stay on their products but are not offered for new ones. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}
