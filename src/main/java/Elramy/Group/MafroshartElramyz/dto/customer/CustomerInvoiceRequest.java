package Elramy.Group.MafroshartElramyz.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerInvoiceRequest(

        @NotBlank(message = "Customer name is required")
        @Size(max = 200)
        String name,

        @NotBlank(message = "Customer phone is required")
        @Pattern(regexp = "\\+?\\d{7,20}", message = "Invalid phone number format")
        String phone,

        @Size(max = 500)
        String address

) {
}