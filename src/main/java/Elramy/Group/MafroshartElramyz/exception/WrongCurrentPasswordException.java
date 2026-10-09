package Elramy.Group.MafroshartElramyz.exception;

/**
 * The current password given when changing one's own password is wrong.
 * A 400 (via IllegalArgumentException), not a 401: it must not sign the user out.
 */
public class WrongCurrentPasswordException extends IllegalArgumentException {
    public WrongCurrentPasswordException() {
        super("كلمة المرور الحالية غير صحيحة");
    }
}
