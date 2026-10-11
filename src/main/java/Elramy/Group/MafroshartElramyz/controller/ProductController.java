package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.dto.product.*;
import Elramy.Group.MafroshartElramyz.services.ProductImportService;
import Elramy.Group.MafroshartElramyz.services.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductImportService productImportService;


    // =========================================================
    // VARIANT LOOKUP (code / barcode scan)
    // =========================================================

    @GetMapping("/variants/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public VariantLookupResponse lookupVariant(@RequestParam("code") String code) {

        return productService.lookupVariant(code);
    }


    // =========================================================
    // LOW STOCK
    // =========================================================

    /** Active products at or below their minimum quantity (header alert). */
    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public List<ProductResponse> getLowStock() {

        return productService.getLowStock();
    }


    // =========================================================
    // BULK IMPORT (EXCEL)
    // =========================================================

    /** Blank template with dropdowns and an instructions sheet. */
    @GetMapping("/import/template")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadImportTemplate() {

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"products-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(productImportService.buildTemplate());
    }

    /**
     * All-or-nothing: 201 with the created products, or 400 with every
     * row error and nothing saved.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductImportResult importProducts(
            @RequestParam("file") MultipartFile file) {

        return productImportService.importFile(file);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @Valid @RequestBody CreateProductRequest request) {

        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {

        return productService.update(id, request);
    }

    // POST and PATCH both set the current price (first price or change).
    // They are kept as two verbs only for compatibility with older clients.
    @PostMapping("/{id}/price")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse setPrice(
            @PathVariable Long id,
            @Valid @RequestBody CreateProductPriceRequest request) {

        return productService.setPrice(id, request);
    }

    @PatchMapping("/{id}/price")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse updatePrice(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePriceRequest request) {

        // Old PATCH contract: when both are sent, profit % wins.
        return productService.setPrice(id, new CreateProductPriceRequest(
                request.purchasePrice(),
                request.profitPercentage(),
                request.profitPercentage() != null ? null : request.sellingPrice()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ProductResponse getById(@PathVariable Long id) {

        return productService.getById(id);
    }


    // =========================================================
    // GET CURRENT PRICE
    // =========================================================

    @GetMapping("/{id}/price")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<ProductPriceResponse> getPrice(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                productService.getPrice(id)
        );
    }


    // =========================================================
    // DELETE (soft)
    // =========================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {

        productService.delete(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public List<ProductResponse> getAll() {

        return productService.getAll();
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public List<ProductResponse> search(
            @RequestParam String keyword) {

        return productService.search(keyword);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void toggleStatus(@PathVariable Long id) {

        productService.toggleStatus(id);
    }
}