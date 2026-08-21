package Elramy.Group.MafroshartElramyz.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record ApiErrorResponse(

        LocalDateTime timestamp,

        int status,

        String message,

        String path,

        Map<String, String> errors

) {
}