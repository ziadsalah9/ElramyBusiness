package Elramy.Group.MafroshartElramyz.mapping;

import Elramy.Group.MafroshartElramyz.enums.customer.CustomerResponse;
import Elramy.Group.MafroshartElramyz.models.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {

        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getActive()
        );
    }
}