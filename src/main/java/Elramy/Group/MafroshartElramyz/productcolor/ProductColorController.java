package Elramy.Group.MafroshartElramyz.productcolor;

import Elramy.Group.MafroshartElramyz.productcolor.ProductColorService.ColorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Colours: everyone reads the shown ones; ADMIN adds, edits, hides and deletes. */
@RestController
@RequestMapping("/api/product-colors")
@RequiredArgsConstructor
public class ProductColorController {

    private final ProductColorService service;

    public record ColorRequest(
            @NotBlank(message = "اكتب اسم اللون") @Size(max = ProductColorService.MAX_NAME, message = "الاسم طويل جداً") String name,
            @Size(max = 7) String hex) {
    }

    /** The shown colours, for product forms. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public List<ColorResponse> list() {
        return service.list(false);
    }

    /** ADMIN: every colour, hidden ones too, with how many items and drafts use each. */
    @GetMapping("/manage")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ColorResponse> manage() {
        return service.list(true);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ColorResponse create(@Valid @RequestBody ColorRequest request) {
        return service.create(request.name(), request.hex());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ColorResponse update(@PathVariable Long id, @Valid @RequestBody ColorRequest request) {
        return service.update(id, request.name(), request.hex());
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ColorResponse toggle(@PathVariable Long id) {
        return service.toggle(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
