package Elramy.Group.MafroshartElramyz.exception;

import Elramy.Group.MafroshartElramyz.dto.product.ProductImportRowError;

import java.time.LocalDateTime;
import java.util.List;

/** ApiError plus the per-row problems of a rejected import. */
public record ImportErrorResponse(

        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        List<ProductImportRowError> errors

) {
}
