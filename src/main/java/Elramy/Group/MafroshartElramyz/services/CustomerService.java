package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.customer.CreateCustomerRequest;
import Elramy.Group.MafroshartElramyz.enums.customer.CustomerDeleteResponse;
import Elramy.Group.MafroshartElramyz.enums.customer.CustomerResponse;
import Elramy.Group.MafroshartElramyz.enums.customer.UpdateCustomerRequest;

import java.util.List;

public interface CustomerService {

    CustomerResponse create(
            CreateCustomerRequest request);

    CustomerResponse update(
            Long id,
            UpdateCustomerRequest request);

    CustomerResponse getById(
            Long id);

    CustomerResponse getByPhone(
            String phone);

    List<CustomerResponse> getAll();

    CustomerResponse toggleStatus(
            Long id);

    CustomerDeleteResponse delete(
            Long id);
}