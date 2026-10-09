package Elramy.Group.MafroshartElramyz.activity;

import java.time.LocalDateTime;
import java.util.List;

public record ActivityLogResponse(

        Long id,
        LocalDateTime createdAt,
        Long userId,
        String username,
        String userFullName,
        ActivityAction action,
        ActivityEntity entityType,
        Long entityId,
        String entityLabel,
        String description,
        /** Field changes, before -> after (empty when there's nothing to show). */
        List<Changes.Change> changes

) {
}
