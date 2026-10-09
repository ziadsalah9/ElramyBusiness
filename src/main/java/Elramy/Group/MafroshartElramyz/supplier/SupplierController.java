package Elramy.Group.MafroshartElramyz.supplier;

import Elramy.Group.MafroshartElramyz.supplier.SupplierDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Suppliers, what we owe them and payments to them: ADMIN-only (like purchases). */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SupplierController {

    private final SupplierService service;

    @GetMapping("/api/suppliers")
    public List<SupplierResponse> list() {
        return service.list();
    }

    @PostMapping("/api/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return service.create(request);
    }

    @PutMapping("/api/suppliers/{id}")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/api/suppliers/{id}/toggle-status")
    public SupplierResponse toggle(@PathVariable Long id) {
        return service.toggleActive(id);
    }

    @GetMapping("/api/suppliers/{id}/account")
    public AccountResponse account(@PathVariable Long id) {
        return service.account(id);
    }

    @PostMapping("/api/suppliers/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return service.pay(id, request);
    }

    @GetMapping("/api/supplier-payments")
    public List<PaymentResponse> payments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.listPayments(from, to);
    }
}
