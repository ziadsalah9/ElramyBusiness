package Elramy.Group.MafroshartElramyz.stocktake;

import Elramy.Group.MafroshartElramyz.stocktake.StocktakeDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Stocktaking (الجرد): ADMIN-only end to end. */
@RestController
@RequestMapping("/api/stocktakes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StocktakeController {

    private final StocktakeService service;

    @GetMapping
    public List<Summary> list() {
        return service.list();
    }

    @PostMapping
    public Summary create(@Valid @RequestBody CreateRequest request) {
        return service.create(request);
    }

    /** Counting view: counted lines only, no system quantities. */
    @GetMapping("/{id}")
    public CountView get(@PathVariable Long id) {
        return service.getForCounting(id);
    }

    @GetMapping("/{id}/review")
    public Review review(@PathVariable Long id) {
        return service.getReview(id);
    }

    @PutMapping("/{id}/lines/{variantId}")
    public CountLine setCount(@PathVariable Long id, @PathVariable Long variantId, @Valid @RequestBody CountRequest request) {
        return service.setCount(id, variantId, request.quantity());
    }

    @PostMapping("/{id}/scan")
    public Scanned scan(@PathVariable Long id, @Valid @RequestBody ScanRequest request) {
        return service.scan(id, request.code());
    }

    @DeleteMapping("/{id}/lines/{variantId}")
    public ResponseEntity<Void> removeLine(@PathVariable Long id, @PathVariable Long variantId) {
        service.removeLine(id, variantId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/lines/{variantId}/reason")
    public ReviewLine setReason(@PathVariable Long id, @PathVariable Long variantId, @Valid @RequestBody ReasonRequest request) {
        return service.setReason(id, variantId, request);
    }

    @GetMapping("/{id}/sheet")
    public ResponseEntity<byte[]> sheet(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stocktake-" + id + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.buildSheet(id));
    }

    @PostMapping(value = "/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCounts(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.importCounts(id, file);
    }

    @PostMapping("/{id}/approve")
    public Review approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/cancel")
    public Summary cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
