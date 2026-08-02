package Elramy.Group.MafroshartElramyz.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;

    private String phone;

    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "branch")
    private List<User> users = new ArrayList<>();

    @OneToMany(mappedBy = "branch")
    private List<ProductStock> stocks = new ArrayList<>();

}