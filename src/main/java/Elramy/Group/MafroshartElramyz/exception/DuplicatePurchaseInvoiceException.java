package Elramy.Group.MafroshartElramyz.exception;

public class DuplicatePurchaseInvoiceException
        extends RuntimeException {

    public DuplicatePurchaseInvoiceException(
            String invoiceNumber) {

        super(
                "Purchase invoice already exists: "
                        + invoiceNumber
        );
    }
}