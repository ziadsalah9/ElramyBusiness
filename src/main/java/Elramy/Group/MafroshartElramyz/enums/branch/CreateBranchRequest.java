package Elramy.Group.MafroshartElramyz.enums.branch;

import jakarta.validation.constraints.NotBlank;

public record CreateBranchRequest(

        @NotBlank
        String name,

        String address,

        String phone

) {}