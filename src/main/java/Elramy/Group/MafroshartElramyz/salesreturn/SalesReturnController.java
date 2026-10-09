package Elramy.Group.MafroshartElramyz.salesreturn;

import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SalesReturnController {

    private final SalesReturnService service;
    private final ExchangeService exchangeService;

    /** Making a return is ADMIN-only. */
    @PostMapping("/api/sales-invoices/{id}/returns")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnResponse create(@PathVariable Long id, @Valid @RequestBody ReturnRequest request) {
        return service.create(id, request);
    }

    /** Exchange: return items and take new ones in one step (ADMIN). */
    @PostMapping("/api/sales-invoices/{id}/exchange")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeResponse exchange(@PathVariable Long id, @Valid @RequestBody ExchangeRequest request) {
        return exchangeService.exchange(id, request);
    }

    @GetMapping("/api/sales-invoices/{id}/returns")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public List<ReturnResponse> forInvoice(@PathVariable Long id) {
        return service.forInvoice(id);
    }

    /** All returns, for the receipts screen. */
    @GetMapping("/api/sales-returns")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public List<ReturnResponse> all(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return service.all(from, to);
    }
}
