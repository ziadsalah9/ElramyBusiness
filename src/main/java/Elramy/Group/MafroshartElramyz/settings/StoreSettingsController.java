package Elramy.Group.MafroshartElramyz.settings;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Everyone reads the settings (receipts, discount limit); admins change them. */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class StoreSettingsController {

    private final StoreSettingsService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public StoreSettingsDto get() {
        return service.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public StoreSettingsDto update(@Valid @RequestBody StoreSettingsDto dto) {
        return service.update(dto);
    }
}
