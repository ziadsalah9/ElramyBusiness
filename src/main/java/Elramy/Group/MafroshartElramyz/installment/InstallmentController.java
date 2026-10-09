package Elramy.Group.MafroshartElramyz.installment;

import Elramy.Group.MafroshartElramyz.installment.InstallmentDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Reading plans and the follow-up list is open to employees too (they
 * collect the payments); creating a plan happens with an ADMIN sale.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class InstallmentController {

    private final InstallmentService service;

    @GetMapping("/api/sales-invoices/{id}/installments")
    public ResponseEntity<PlanResponse> forInvoice(@PathVariable Long id) {
        return service.forInvoice(id).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    /** Unpaid installments due within the next {@code days} days (overdue ones included). */
    @GetMapping("/api/installments/due")
    public List<DueRow> due(@RequestParam(defaultValue = "0") int days) {
        return service.due(days);
    }
}
