package Elramy.Group.MafroshartElramyz.exception;

public class DuplicateBranchException extends RuntimeException{

    public DuplicateBranchException(String name){

        super("Branch '" + name + "' already exists.");

    }

}