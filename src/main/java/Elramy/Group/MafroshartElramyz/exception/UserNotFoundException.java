package Elramy.Group.MafroshartElramyz.exception;

public class UserNotFoundException extends RuntimeException{

    public UserNotFoundException(String username){

        super("user with name " + username + " not found.");

    }

    public UserNotFoundException(Long id){

        super("user with id " + id + " not found.");

    }

}