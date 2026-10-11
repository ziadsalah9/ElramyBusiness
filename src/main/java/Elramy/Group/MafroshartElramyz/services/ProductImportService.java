package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.dto.product.ProductImportResult;
import org.springframework.web.multipart.MultipartFile;

public interface ProductImportService {

    /** The blank .xlsx template users fill in. */
    byte[] buildTemplate();

    /**
     * Validates every row of an uploaded sheet and, only if all of them
     * are valid, creates the products (and their prices) in a single
     * transaction.
     */
    ProductImportResult importFile(MultipartFile file);
}
