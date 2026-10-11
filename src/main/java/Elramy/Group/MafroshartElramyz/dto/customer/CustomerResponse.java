package Elramy.Group.MafroshartElramyz.dto.customer;

public record CustomerResponse(

        Long id,
        String name,
        String phone,
        String secondaryPhone,
        String address,
        Boolean active

) {
}