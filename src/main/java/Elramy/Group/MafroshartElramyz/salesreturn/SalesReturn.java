package Elramy.Group.MafroshartElramyz.salesreturn;

import Elramy.Group.MafroshartElramyz.models.BaseEntity;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.Customer;
import Elramy.Group.MafroshartElramyz.models.SalesInvoice;
import Elramy.Group.MafroshartElramyz.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A partial or full return of a sales invoice (V16). Its value goes back
 * as cash and/or as credit against what the customer owes:
 * cashRefund + creditAmount = total.
 */
@Entity
@Table(name = "sales_returns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesReturn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private SalesInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal cashRefund;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal creditAmount;

    @Column(length = 500)
    private String reason;

    /** Set when this return is part of an exchange: the sale of the new items (V22). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_invoice_id")
    private SalesInvoice exchangeInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "salesReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<SalesReturnItem> items = new ArrayList<>();
}
