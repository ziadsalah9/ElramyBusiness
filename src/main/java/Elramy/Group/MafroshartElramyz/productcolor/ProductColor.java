package Elramy.Group.MafroshartElramyz.productcolor;

import Elramy.Group.MafroshartElramyz.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** A colour (أبيض، كحلي، ...), managed by admins (V25). Colour/size rows reference it by name. */
@Entity
@Table(name = "product_colors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductColor extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** Optional swatch shown next to the name (#rrggbb, lower case). */
    @Column(length = 7)
    private String hex;

    /** Hidden colours stay on their items but are not offered for new ones. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}
