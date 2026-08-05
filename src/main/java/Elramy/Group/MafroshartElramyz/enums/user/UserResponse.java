package Elramy.Group.MafroshartElramyz.enums.user;

import Elramy.Group.MafroshartElramyz.enums.Role;

public record UserResponse(

        Long id,

        String username,

        String fullName,

        Role role,

        Long branchId,

        String branchName,

        Boolean active

) {
}