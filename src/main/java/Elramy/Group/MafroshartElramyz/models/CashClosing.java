package Elramy.Group.MafroshartElramyz.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "cash_closings",
        uniqueConstraints = @UniqueConstraint(
                name = "cash_closings_branch_date_key",
                columnNames = {"branch_id", "closing_date"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashClosing extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Column(nullable = false)
    private LocalDate closingDate;

    @Builder.Default
    private Integer count5 = 0;

    @Builder.Default
    private Integer count10 = 0;

    @Builder.Default
    private Integer count20 = 0;

    @Builder.Default
    private Integer count50 = 0;

    @Builder.Default
    private Integer count100 = 0;

    @Builder.Default
    private Integer count200 = 0;

    @Column(nullable = false)
    private BigDecimal cashSalesTotal;

    @Column(nullable = false)
    private BigDecimal countedTotal;

    @Column(nullable = false)
    private BigDecimal difference;

    private String notes;
}
