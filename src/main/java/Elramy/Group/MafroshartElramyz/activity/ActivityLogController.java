package Elramy.Group.MafroshartElramyz.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** Activity log - ADMIN only. */
@RestController
@RequestMapping("/api/activity-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ActivityLogController {

    private final ActivityLogService service;

    /** Newest first, paginated; every filter is optional. Dates are inclusive. */
    @GetMapping
    public Map<String, Object> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) ActivityEntity entityType,
            @RequestParam(required = false) ActivityAction action,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<ActivityLogResponse> result = service.search(userId, entityType, action, entityId, from, to, q, page, size);
        // A plain, stable JSON shape instead of Spring's Page serialization.
        return Map.of(
                "content", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages());
    }
}
