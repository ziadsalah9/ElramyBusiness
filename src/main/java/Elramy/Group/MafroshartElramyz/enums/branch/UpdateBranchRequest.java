package Elramy.Group.MafroshartElramyz.enums.branch;

import jakarta.validation.constraints.NotBlank;

public record UpdateBranchRequest(

        @NotBlank
        String name,

        String address,

        String phone

) {}