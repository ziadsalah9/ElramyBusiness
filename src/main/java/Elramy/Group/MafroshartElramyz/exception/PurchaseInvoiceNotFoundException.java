package Elramy.Group.MafroshartElramyz.exception;

public class PurchaseInvoiceNotFoundException
        extends RuntimeException {

    public PurchaseInvoiceNotFoundException(Long id) {

        super(
                "Purchase invoice not found with id: "
                        + id
        );
    }

    public PurchaseInvoiceNotFoundException(
            String invoiceNumber) {

        super(
                "Purchase invoice not found with number: "
                        + invoiceNumber
        );
    }
}