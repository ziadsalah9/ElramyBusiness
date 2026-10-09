package Elramy.Group.MafroshartElramyz.enums.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(

        @NotBlank(message = "Customer name is required")
        @Size(max = 200)
        String name,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "\\+?\\d{7,20}", message = "Invalid phone number format")
        String phone,

        // Optional; null skips the pattern check.
        @Pattern(regexp = "\\+?\\d{7,20}", message = "Invalid secondary phone number format")
        String secondaryPhone,

        @Size(max = 500)
        String address

) {
}