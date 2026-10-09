package Elramy.Group.MafroshartElramyz.exception;

/** Too many failed logins for this username or address (HTTP 429). */
public class LoginBlockedException extends RuntimeException {
    public LoginBlockedException(String message) {
        super(message);
    }
}
