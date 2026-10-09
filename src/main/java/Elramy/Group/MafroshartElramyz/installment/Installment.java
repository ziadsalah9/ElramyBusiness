package Elramy.Group.MafroshartElramyz.installment;

import Elramy.Group.MafroshartElramyz.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One monthly installment; paid when paidAmount reaches amount. */
@Entity
@Table(name = "installments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Installment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private InstallmentPlan plan;

    @Column(nullable = false)
    private Integer seq;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    private LocalDateTime paidAt;

    public BigDecimal remaining() {
        return amount.subtract(paidAmount);
    }
}
