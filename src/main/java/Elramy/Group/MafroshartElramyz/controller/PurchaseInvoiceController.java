package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.CreatePurchaseInvoiceRequest;
import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.PurchaseInvoiceResponse;
import Elramy.Group.MafroshartElramyz.services.PurchaseInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-invoices")
@RequiredArgsConstructor
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService purchaseInvoiceService;


    // =========================================================
    // CREATE PURCHASE INVOICE
    // =========================================================

    @PostMapping
    public ResponseEntity<PurchaseInvoiceResponse> create(
            @Valid @RequestBody CreatePurchaseInvoiceRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(purchaseInvoiceService.create(request));
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseInvoiceResponse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                purchaseInvoiceService.getById(id)
        );
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @GetMapping
    public ResponseEntity<List<PurchaseInvoiceResponse>> getAll() {

        return ResponseEntity.ok(
                purchaseInvoiceService.getAll()
        );
    }


    // =========================================================
    // GET BY INVOICE NUMBER
    // =========================================================

    @GetMapping("/number/{invoiceNumber}")
    public ResponseEntity<PurchaseInvoiceResponse> getByInvoiceNumber(
            @PathVariable String invoiceNumber) {

        return ResponseEntity.ok(
                purchaseInvoiceService
                        .getByInvoiceNumber(invoiceNumber)
        );
    }


}