package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.enums.stock.StockResponse;
import Elramy.Group.MafroshartElramyz.enums.stock.StockTransactionResponse;
import Elramy.Group.MafroshartElramyz.services.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;


    // =========================================================
    // GET STOCK BY ID
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<StockResponse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                stockService.getById(id)
        );
    }


    // =========================================================
    // GET STOCK BY PRODUCT + BRANCH
    // =========================================================

    @GetMapping("/product/{productId}/branch/{branchId}")
    public ResponseEntity<StockResponse> getByProductAndBranch(
            @PathVariable Long productId,
            @PathVariable Long branchId) {

        return ResponseEntity.ok(
                stockService.getByProductAndBranch(
                        productId,
                        branchId
                )
        );
    }


    // =========================================================
    // GET ALL STOCK
    // =========================================================

    @GetMapping
    public ResponseEntity<List<StockResponse>> getAll() {

        return ResponseEntity.ok(
                stockService.getAll()
        );
    }


    // =========================================================
    // GET STOCK BY PRODUCT
    // =========================================================

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<StockResponse>> getByProduct(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                stockService.getByProduct(productId)
        );
    }


    // =========================================================
    // GET STOCK BY BRANCH
    // =========================================================

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<StockResponse>> getByBranch(
            @PathVariable Long branchId) {

        return ResponseEntity.ok(
                stockService.getByBranch(branchId)
        );
    }


    // =========================================================
    // GET LOW STOCK
    // =========================================================

    @GetMapping("/low-stock")
    public ResponseEntity<List<StockResponse>> getLowStock() {

        return ResponseEntity.ok(
                stockService.getLowStock()
        );
    }


    // =========================================================
    // TRANSACTIONS BY PRODUCT STOCK
    // =========================================================

    @GetMapping("/{productStockId}/transactions")
    public ResponseEntity<List<StockTransactionResponse>> getTransactions(
            @PathVariable Long productStockId) {

        return ResponseEntity.ok(
                stockService.getTransactions(productStockId)
        );
    }


    // =========================================================
    // TRANSACTIONS BY PRODUCT
    // =========================================================

    @GetMapping("/product/{productId}/transactions")
    public ResponseEntity<List<StockTransactionResponse>> getTransactionsByProduct(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                stockService.getTransactionsByProduct(productId)
        );
    }


    // =========================================================
    // TRANSACTIONS BY BRANCH
    // =========================================================

    @GetMapping("/branch/{branchId}/transactions")
    public ResponseEntity<List<StockTransactionResponse>> getTransactionsByBranch(
            @PathVariable Long branchId) {

        return ResponseEntity.ok(
                stockService.getTransactionsByBranch(branchId)
        );
    }
}