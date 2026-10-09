package Elramy.Group.MafroshartElramyz.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Without an explicit entry point Spring Security answers every
 * unauthenticated request with 403, which the frontend reads as
 * "you lack permission" instead of "your session expired". Returning
 * 401 keeps the client's 401 -> clear token -> redirect to /login
 * behaviour working.
 *
 * The body is written by hand rather than through an ObjectMapper
 * because this app runs on Spring Boot 4, where Jackson 3 is the
 * default and the Jackson 2 ObjectMapper is not auto-configured as a
 * bean. The payload here is trivial and always the same shape.
 */
@Component
public class JwtAuthenticationEntryPoint
        implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {

        String body = """
                {
                  "timestamp": "%s",
                  "status": 401,
                  "error": "Unauthorized",
                  "message": "Authentication is required to access this resource.",
                  "path": "%s"
                }
                """.formatted(
                LocalDateTime.now(),
                escape(request.getRequestURI())
        );

        response.setStatus(
                HttpStatus.UNAUTHORIZED.value()
        );
        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body);
    }

    /** Keeps a hostile request URI from breaking out of the JSON string. */
    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}