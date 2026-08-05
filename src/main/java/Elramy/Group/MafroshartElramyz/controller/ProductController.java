package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.enums.product.*;
import Elramy.Group.MafroshartElramyz.services.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @Valid @RequestBody CreateProductRequest request) {

        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {

        return productService.update(id, request);
    }

    @PostMapping("/{id}/price")
    public ResponseEntity<ProductResponse> createPrice(
            @PathVariable Long id,
            @RequestBody CreateProductPriceRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        productService.createPrice(
                                id,
                                request
                        )
                );
    }

    @PatchMapping("/{id}/price")
    public ProductResponse updatePrice(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePriceRequest request) {

        return productService.updatePrice(id, request);
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {

        return productService.getById(id);
    }

    @GetMapping
    public List<ProductResponse> getAll() {

        return productService.getAll();
    }

    @GetMapping("/search")
    public List<ProductResponse> search(
            @RequestParam String keyword) {

        return productService.search(keyword);
    }

    @PatchMapping("/{id}/toggle-status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void toggleStatus(@PathVariable Long id) {

        productService.toggleStatus(id);
    }



}