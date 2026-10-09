package Elramy.Group.MafroshartElramyz.expense;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.models.BaseEntity;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A shop expense (V20). A CASH expense comes out of the drawer on expenseDate. */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String expenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseCategory category;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
