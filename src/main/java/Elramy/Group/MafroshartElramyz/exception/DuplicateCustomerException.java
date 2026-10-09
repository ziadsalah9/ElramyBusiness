package Elramy.Group.MafroshartElramyz.exception;

public class DuplicateCustomerException
        extends RuntimeException {

    public DuplicateCustomerException(String phone) {
        super("customer with phone " + phone + " already exists.");
    }
}