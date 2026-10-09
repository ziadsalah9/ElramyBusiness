package Elramy.Group.MafroshartElramyz.settings;

import Elramy.Group.MafroshartElramyz.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** The single store settings row (V16, id = 1). */
@Entity
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreSettings extends BaseEntity {

    public static final long ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 200)
    private String storeName;

    @Column(length = 100)
    private String phone;

    @Column(length = 300)
    private String address;

    @Column(length = 300)
    private String receiptFooter;

    /** Largest discount (percent of the invoice before discounts) an employee may give. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal employeeMaxDiscountPercent;
}
