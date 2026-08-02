package Elramy.Group.MafroshartElramyz.exception;

public class BranchNotFoundException extends RuntimeException{

    public BranchNotFoundException(Long id){

        super("Branch with id " + id + " not found.");

    }

}