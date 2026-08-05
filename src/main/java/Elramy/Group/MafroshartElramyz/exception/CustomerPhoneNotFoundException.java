package Elramy.Group.MafroshartElramyz.exception;

public class CustomerPhoneNotFoundException extends RuntimeException{

    public CustomerPhoneNotFoundException(String phone){

        super("customer with phone " + phone + " not found.");

    }

}