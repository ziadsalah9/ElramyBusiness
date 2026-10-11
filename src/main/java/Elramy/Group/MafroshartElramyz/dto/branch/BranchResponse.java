package Elramy.Group.MafroshartElramyz.dto.branch;

public record BranchResponse(

        Long id,

        String name,

        String address,

        String phone,

        Boolean active

) {}