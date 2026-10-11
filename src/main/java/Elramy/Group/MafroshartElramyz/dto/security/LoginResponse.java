package Elramy.Group.MafroshartElramyz.dto.security;

import Elramy.Group.MafroshartElramyz.enums.Role;

public record LoginResponse(

        String token,

        Long userId,

        String username,

        String fullName,

        Role role,

        Long branchId,

        String branchName

) {
}