package Elramy.Group.MafroshartElramyz.activity;

import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Records who changed what. Called by the services right after a change,
 * inside the same transaction: if the change rolls back, so does its log
 * entry, so the log only ever shows changes that really happened.
 *
 * Deliberately independent of UserService (which itself writes log
 * entries): the current user is read from the security context.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ActivityLogService {

    private final ActivityLogRepository repository;
    private final UserRepository userRepository;

    public void record(ActivityEntity entity, ActivityAction action, Long entityId, String label, String description) {
        record(entity, action, entityId, label, description, null);
    }

    public void record(ActivityEntity entity, ActivityAction action, Long entityId, String label,
                       String description, Changes changes) {
        User user = currentUser();
        repository.save(ActivityLog.builder()
                .userId(user != null ? user.getId() : null)
                .username(user != null ? user.getUsername() : currentUsername())
                .userFullName(user != null ? user.getFullName() : null)
                .action(action)
                .entityType(entity)
                .entityId(entityId)
                .entityLabel(truncate(label, 255))
                .description(description)
                .changes(changes == null || changes.isEmpty() ? null : toJson(changes.list()))
                .build());
    }

    @Transactional(readOnly = true)
    public Page<ActivityLogResponse> search(Long userId, ActivityEntity entityType, ActivityAction action, Long entityId,
                                            LocalDate from, LocalDate to, String q, int page, int size) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return repository.search(userId, entityType, action, entityId,
                        from != null ? from.atStartOfDay() : null,
                        to != null ? to.plusDays(1).atStartOfDay() : null,   // inclusive "to" day
                        query,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)))
                .map(ActivityLogService::toResponse);
    }

    static ActivityLogResponse toResponse(ActivityLog a) {
        return new ActivityLogResponse(a.getId(), a.getCreatedAt(), a.getUserId(), a.getUsername(), a.getUserFullName(),
                a.getAction(), a.getEntityType(), a.getEntityId(), a.getEntityLabel(), a.getDescription(), parseJson(a.getChanges()));
    }

    private User currentUser() {
        String username = currentUsername();
        return username == null ? null : userRepository.findByUsername(username).orElse(null);
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return null;
        return auth.getName();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ---------------------------------------------------------------- tiny JSON (list of {field, before, after})

    static String toJson(List<Changes.Change> changes) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < changes.size(); i++) {
            Changes.Change c = changes.get(i);
            if (i > 0) b.append(',');
            b.append("{\"field\":").append(str(c.field()))
             .append(",\"before\":").append(str(c.before()))
             .append(",\"after\":").append(str(c.after())).append('}');
        }
        return b.append(']').toString();
    }

    private static String str(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
                }
            }
        }
        return b.append('"').toString();
    }

    /** Parses what {@link #toJson} wrote (and nothing more general). */
    static List<Changes.Change> parseJson(String json) {
        List<Changes.Change> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        int[] pos = {json.indexOf('[') + 1};
        while (true) {
            int start = json.indexOf('{', pos[0]);
            if (start < 0) break;
            pos[0] = start + 1;
            String field = null, before = null, after = null;
            for (int k = 0; k < 3; k++) {
                String key = readString(json, pos);
                pos[0] = json.indexOf(':', pos[0]) + 1;
                String value = readValue(json, pos);
                switch (key) {
                    case "field" -> field = value;
                    case "before" -> before = value;
                    case "after" -> after = value;
                    default -> { }
                }
            }
            out.add(new Changes.Change(field, before, after));
            pos[0] = json.indexOf('}', pos[0]) + 1;
        }
        return out;
    }

    private static String readValue(String json, int[] pos) {
        while (Character.isWhitespace(json.charAt(pos[0]))) pos[0]++;
        if (json.startsWith("null", pos[0])) {
            pos[0] += 4;
            return null;
        }
        return readString(json, pos);
    }

    private static String readString(String json, int[] pos) {
        int i = json.indexOf('"', pos[0]) + 1;
        StringBuilder b = new StringBuilder();
        while (i < json.length()) {
            char ch = json.charAt(i++);
            if (ch == '"') break;
            if (ch == '\\') {
                char e = json.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> { b.append((char) Integer.parseInt(json.substring(i, i + 4), 16)); i += 4; }
                    default -> b.append(e);
                }
            } else {
                b.append(ch);
            }
        }
        pos[0] = i;
        return b.toString();
    }
}
