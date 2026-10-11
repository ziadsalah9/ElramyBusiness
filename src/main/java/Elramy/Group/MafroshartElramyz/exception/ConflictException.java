package Elramy.Group.MafroshartElramyz.exception;

/** The request is valid but clashes with the current state (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
