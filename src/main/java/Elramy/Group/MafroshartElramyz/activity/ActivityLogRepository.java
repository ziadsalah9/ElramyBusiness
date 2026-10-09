package Elramy.Group.MafroshartElramyz.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /** Newest first; every filter is optional (null = any). */
    @Query("""
        SELECT a FROM ActivityLog a
        WHERE (:userId IS NULL OR a.userId = :userId)
          AND (:entityType IS NULL OR a.entityType = :entityType)
          AND (:action IS NULL OR a.action = :action)
          AND (:entityId IS NULL OR a.entityId = :entityId)
          AND (CAST(:from AS timestamp) IS NULL OR a.createdAt >= :from)
          AND (CAST(:to AS timestamp) IS NULL OR a.createdAt < :to)
          AND (:q IS NULL
               OR LOWER(a.description) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
               OR LOWER(COALESCE(a.entityLabel, '')) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
               OR LOWER(COALESCE(a.userFullName, '')) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        ORDER BY a.createdAt DESC, a.id DESC
        """)
    Page<ActivityLog> search(@Param("userId") Long userId,
                             @Param("entityType") ActivityEntity entityType,
                             @Param("action") ActivityAction action,
                             @Param("entityId") Long entityId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             @Param("q") String q,
                             Pageable pageable);
}
