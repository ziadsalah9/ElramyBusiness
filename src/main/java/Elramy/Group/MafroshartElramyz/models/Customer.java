package Elramy.Group.MafroshartElramyz.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    // Optional extra contact number. Not unique: relatives often share one.
    @Column(name = "secondary_phone", length = 20)
    private String secondaryPhone;

    private String address;

    @Builder.Default
    private Boolean active = true;
}