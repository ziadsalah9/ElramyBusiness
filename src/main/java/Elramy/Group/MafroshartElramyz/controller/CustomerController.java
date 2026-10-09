package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.enums.customer.CreateCustomerRequest;
import Elramy.Group.MafroshartElramyz.enums.customer.CustomerDeleteResponse;
import Elramy.Group.MafroshartElramyz.enums.customer.CustomerResponse;
import Elramy.Group.MafroshartElramyz.enums.customer.UpdateCustomerRequest;
import Elramy.Group.MafroshartElramyz.services.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;


    // =========================================================
    // CREATE
    // =========================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse create(
            @Valid @RequestBody CreateCustomerRequest request) {

        return customerService.create(request);
    }


    // =========================================================
    // UPDATE
    // =========================================================

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomerRequest request) {

        return customerService.update(id, request);
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<CustomerResponse>> getAll() {

        return ResponseEntity.ok(
                customerService.getAll()
        );
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<CustomerResponse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                customerService.getById(id)
        );
    }


    // =========================================================
    // GET BY PHONE
    // =========================================================

    @GetMapping("/phone/{phone}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<CustomerResponse> getByPhone(
            @PathVariable String phone) {

        return ResponseEntity.ok(
                customerService.getByPhone(phone)
        );
    }


    // =========================================================
    // TOGGLE STATUS
    // =========================================================

    @PatchMapping("/{id}/toggle-status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void toggleStatus(
            @PathVariable Long id) {

        customerService.toggleStatus(id);
    }


    // =========================================================
    // DELETE
    // =========================================================

    // Hard delete when the customer has no invoices, otherwise
    // deactivate; the response says which happened.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerDeleteResponse delete(
            @PathVariable Long id) {

        return customerService.delete(id);
    }
}