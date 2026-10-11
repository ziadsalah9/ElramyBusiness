package Elramy.Group.MafroshartElramyz.activity;

import Elramy.Group.MafroshartElramyz.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** One recorded change: who, what, on which record, and before -> after. */
@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String username;
    private String userFullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ActivityEntity entityType;

    private Long entityId;

    /** Human name of the record, e.g. "INV-1A2B" or "لحاف قطن (PRD-…)". */
    private String entityLabel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** JSON list of {field, before, after}; null when nothing to show. */
    @Column(columnDefinition = "TEXT")
    private String changes;
}
