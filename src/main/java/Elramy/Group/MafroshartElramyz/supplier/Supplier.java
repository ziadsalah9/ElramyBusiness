package Elramy.Group.MafroshartElramyz.supplier;

import Elramy.Group.MafroshartElramyz.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** Someone we buy stock from (V20). */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(length = 500)
    private String notes;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}
