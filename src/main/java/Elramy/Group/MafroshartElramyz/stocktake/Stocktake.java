package Elramy.Group.MafroshartElramyz.stocktake;

import Elramy.Group.MafroshartElramyz.models.BaseEntity;
import Elramy.Group.MafroshartElramyz.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** A stock count of the whole store or of some product types (see V14). */
@Entity
@Table(name = "stocktakes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stocktake extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    /** Comma separated product types; null = every product. */
    @Column(columnDefinition = "TEXT")
    private String scopeTypes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StocktakeStatus status;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;

    @Column(precision = 18, scale = 2)
    private BigDecimal shortageCost;
    @Column(precision = 18, scale = 2)
    private BigDecimal surplusCost;
    @Column(precision = 18, scale = 2)
    private BigDecimal shortageSell;
    @Column(precision = 18, scale = 2)
    private BigDecimal surplusSell;

    @OneToMany(mappedBy = "stocktake", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<StocktakeLine> lines = new ArrayList<>();

    /** The product types counted; empty = every product. */
    public List<String> scope() {
        if (scopeTypes == null || scopeTypes.isBlank()) return List.of();
        return Arrays.stream(scopeTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .toList();
    }

    public void setScope(List<String> types) {
        this.scopeTypes = types == null || types.isEmpty() ? null
                : types.stream().distinct().map(String::trim).filter(t -> !t.isEmpty()).collect(Collectors.joining(","));
    }

    public boolean isOpen() {
        return status == StocktakeStatus.OPEN;
    }
}
