package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.enums.stock.StockAdjustmentRequest;
import Elramy.Group.MafroshartElramyz.enums.stock.StockAdjustmentResponse;
import Elramy.Group.MafroshartElramyz.services.StockAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-adjustments")
@RequiredArgsConstructor
public class StockAdjustmentController {

    private final StockAdjustmentService stockAdjustmentService;


    // =========================================================
    // CREATE ADJUSTMENT
    // =========================================================

    @PostMapping
    public ResponseEntity<StockAdjustmentResponse> create(
            @Valid @RequestBody StockAdjustmentRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(stockAdjustmentService.create(request));
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<StockAdjustmentResponse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                stockAdjustmentService.getById(id)
        );
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @GetMapping
    public ResponseEntity<List<StockAdjustmentResponse>> getAll() {

        return ResponseEntity.ok(
                stockAdjustmentService.getAll()
        );
    }


    // =========================================================
    // GET BY PRODUCT STOCK
    // =========================================================

    @GetMapping("/product-stock/{productStockId}")
    public ResponseEntity<List<StockAdjustmentResponse>> getByProductStock(
            @PathVariable Long productStockId) {

        return ResponseEntity.ok(
                stockAdjustmentService.getByProductStock(productStockId)
        );
    }


    // =========================================================
    // GET BY BRANCH
    // =========================================================

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<StockAdjustmentResponse>> getByBranch(
            @PathVariable Long branchId) {

        return ResponseEntity.ok(
                stockAdjustmentService.getByBranch(branchId)
        );
    }
}