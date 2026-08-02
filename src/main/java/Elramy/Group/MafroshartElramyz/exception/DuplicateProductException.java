package Elramy.Group.MafroshartElramyz.exception;

public class DuplicateProductException extends RuntimeException {

    public DuplicateProductException(String field) {
        super(field + " already exists.");
    }

}