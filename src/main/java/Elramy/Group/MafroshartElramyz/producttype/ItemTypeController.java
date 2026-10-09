package Elramy.Group.MafroshartElramyz.producttype;

import Elramy.Group.MafroshartElramyz.producttype.ItemTypeService.ItemTypeResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Product types: everyone reads the shown ones; ADMIN adds, renames, hides and deletes. */
@RestController
@RequestMapping("/api/product-types")
@RequiredArgsConstructor
public class ItemTypeController {

    private final ItemTypeService service;

    public record NameRequest(@NotBlank(message = "اكتب اسم النوع") @Size(max = ItemTypeService.MAX_NAME, message = "الاسم طويل جداً") String name) {
    }

    /** The shown types, for product forms and filters. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public List<ItemTypeResponse> list() {
        return service.list(false);
    }

    /** ADMIN: every type, hidden ones too, with how many products and drafts use each. */

    @GetMapping("/manage")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ItemTypeResponse> manage() {
        return service.list(true);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemTypeResponse create(@Valid @RequestBody NameRequest request) {
        return service.create(request.name());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ItemTypeResponse rename(@PathVariable Long id, @Valid @RequestBody NameRequest request) {
        return service.rename(id, request.name());
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ItemTypeResponse toggle(@PathVariable Long id) {
        return service.toggle(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
