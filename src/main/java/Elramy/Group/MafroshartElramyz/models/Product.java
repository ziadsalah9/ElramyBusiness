package Elramy.Group.MafroshartElramyz.models;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    @Column(nullable = false,unique = true)
    private String code;

    @Column(unique = true)
    private String barcode;

    @Column(nullable = false)
    private String name;

    private String model;

    /*
      بطانية
      لحاف
      فوطة
      كوفرتة
      ملاية
      ....
     */
    @Enumerated(EnumType.STRING)
    private ProductType itemType;

    private String color;

    private String size;

//    @Column(nullable = false)
//    private BigDecimal purchasePrice;
//
//    @Column(nullable = false)
//    private BigDecimal  profitPercentage;
//
//    @Column(nullable = false)
//    private BigDecimal  sellingPrice;

    @Builder.Default
    private Integer minimumQuantity = 0;
    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "product",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<ProductPrice> prices = new ArrayList<>();


}