package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.dto.product.ProductDraftRequest;
import Elramy.Group.MafroshartElramyz.dto.product.ProductDraftResponse;
import Elramy.Group.MafroshartElramyz.dto.product.ProductResponse;
import Elramy.Group.MafroshartElramyz.services.ProductDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Incomplete products from Excel imports ("منتجات غير مكتملة"). ADMIN only. */
@RestController
@RequestMapping("/api/product-drafts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ProductDraftController {

    private final ProductDraftService draftService;

    @GetMapping
    public List<ProductDraftResponse> list() {
        return draftService.list();
    }

    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("count", draftService.count());
    }

    @PutMapping("/{id}")
    public ProductDraftResponse update(@PathVariable Long id, @RequestBody ProductDraftRequest request) {
        return draftService.update(id, request);
    }

    /** Moves the draft into the system as a real product. */
    @PostMapping("/{id}/complete")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse complete(@PathVariable Long id) {
        return draftService.complete(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        draftService.delete(id);
    }
}
