package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.enums.salesInvoice.SalesInvoiceRequest;
import Elramy.Group.MafroshartElramyz.enums.salesInvoice.SalesInvoiceResponse;
import Elramy.Group.MafroshartElramyz.services.SalesInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales-invoices")
@RequiredArgsConstructor
public class SalesInvoiceController {

    private final SalesInvoiceService salesInvoiceService;


    // =========================================================
    // CREATE SALES INVOICE
    // =========================================================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<SalesInvoiceResponse> create(
            @Valid @RequestBody SalesInvoiceRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(salesInvoiceService.create(request));
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<SalesInvoiceResponse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                salesInvoiceService.getById(id)
        );
    }


    // =========================================================
    // GET BY INVOICE NUMBER
    // =========================================================

    @GetMapping("/number/{invoiceNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<SalesInvoiceResponse> getByInvoiceNumber(
            @PathVariable String invoiceNumber) {

        return ResponseEntity.ok(
                salesInvoiceService
                        .getByInvoiceNumber(invoiceNumber)
        );
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<SalesInvoiceResponse>> getAll() {

        return ResponseEntity.ok(
                salesInvoiceService.getAll()
        );
    }


    // =========================================================
    // GET BY BRANCH
    // =========================================================

    @GetMapping("/branch/{branchId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<SalesInvoiceResponse>> getByBranch(
            @PathVariable Long branchId) {

        return ResponseEntity.ok(
                salesInvoiceService.getByBranch(branchId)
        );
    }


    // =========================================================
    // GET BY CASHIER
    // =========================================================

    @GetMapping("/cashier/{cashierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<SalesInvoiceResponse>> getByCashier(
            @PathVariable Long cashierId) {

        return ResponseEntity.ok(
                salesInvoiceService.getByCashier(cashierId)
        );
    }

    @GetMapping("/customer/{phone}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<SalesInvoiceResponse>> getByCustomerPhone(
            @PathVariable String phone) {

        return ResponseEntity.ok(
                salesInvoiceService.getByPhone(phone)
        );
    }
}