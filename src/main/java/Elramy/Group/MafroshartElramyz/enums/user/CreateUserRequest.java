package Elramy.Group.MafroshartElramyz.enums.user;

import Elramy.Group.MafroshartElramyz.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 10, message = "Password must be at least 10 characters")
        String password,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotNull(message = "Role is required")
        Role role,

        Long branchId

) {
}