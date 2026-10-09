package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.enums.cashClosing.CashClosingRequest;
import Elramy.Group.MafroshartElramyz.enums.cashClosing.CashClosingResponse;
import Elramy.Group.MafroshartElramyz.enums.cashClosing.CashClosingSummary;
import Elramy.Group.MafroshartElramyz.services.CashClosingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cash-closings")
@RequiredArgsConstructor
public class CashClosingController {

    private final CashClosingService cashClosingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<CashClosingResponse> closeCash(@Valid @RequestBody CashClosingRequest request) {
        return ResponseEntity.ok(cashClosingService.closeCash(request));
    }

    @GetMapping("/branches/{branchId}/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<CashClosingSummary> getSummary(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closingDate) {
        return ResponseEntity.ok(cashClosingService.calculateSummary(branchId, closingDate));
    }

    @GetMapping("/branches/{branchId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<CashClosingResponse>> getByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(cashClosingService.getAllByBranch(branchId));
    }

    @GetMapping("/dates/{closingDate}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CashClosingResponse>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closingDate) {
        return ResponseEntity.ok(cashClosingService.getAllByDate(closingDate));
    }

    @GetMapping("/branches/{branchId}/dates/{closingDate}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<CashClosingResponse> getByBranchAndDate(
            @PathVariable Long branchId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closingDate) {
        return ResponseEntity.ok(cashClosingService.getByBranchAndDate(branchId, closingDate));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CashClosingResponse>> getAll() {
        return ResponseEntity.ok(cashClosingService.getAll());
    }
}
