package Elramy.Group.MafroshartElramyz.exception;

import Elramy.Group.MafroshartElramyz.dto.product.ProductImportRowError;
import lombok.Getter;

import java.util.List;

/**
 * Thrown when an uploaded product sheet has invalid rows. The import is
 * all-or-nothing, so nothing was saved; the errors list every problem
 * found so the user can fix the file in one pass.
 */
@Getter
public class ProductImportValidationException extends RuntimeException {

    private final List<ProductImportRowError> errors;

    public ProductImportValidationException(String message, List<ProductImportRowError> errors) {
        super(message);
        this.errors = errors;
    }
}
