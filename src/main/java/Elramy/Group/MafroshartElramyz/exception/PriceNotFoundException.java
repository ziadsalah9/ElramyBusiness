package Elramy.Group.MafroshartElramyz.exception;

public class PriceNotFoundException
        extends RuntimeException {

    public PriceNotFoundException(Long productId) {
        super("no current price set for product with id " + productId);
    }
}