package Elramy.Group.MafroshartElramyz.reports;

import Elramy.Group.MafroshartElramyz.reports.ReportDtos.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Sales and profit reports: ADMIN-only (they include cost and profit). */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService service;
    private final DemandService demand;

    @GetMapping
    public Report report(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.build(from, to);
    }

    /** Sales and demand per colour/size over the last {@code days} days (7-365). */
    @GetMapping("/demand")
    public DemandService.Report demand(@RequestParam(defaultValue = "90") int days) {
        return demand.build(days);
    }
}
