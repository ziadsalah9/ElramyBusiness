package Elramy.Group.MafroshartElramyz.dto.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A signed-in user changing their own password. */
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        @Size(max = 100)
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(max = 100)
        String newPassword

) {
}
