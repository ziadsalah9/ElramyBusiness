package Elramy.Group.MafroshartElramyz.customeraccount;

import Elramy.Group.MafroshartElramyz.customeraccount.CustomerAccountDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Customer balance, statement and payments; open to admins and employees. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class CustomerAccountController {

    private final CustomerAccountService service;

    @GetMapping("/api/customers/{id}/account")
    public AccountResponse account(@PathVariable Long id) {
        return service.account(id);
    }

    @PostMapping("/api/customers/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return service.recordPayment(id, request);
    }

    @GetMapping("/api/customer-payments")
    public List<PaymentResponse> payments() {
        return service.listPayments();
    }
}
