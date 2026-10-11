package Elramy.Group.MafroshartElramyz.exception;

public class SalesInvoiceNotFoundException
        extends RuntimeException {

    public SalesInvoiceNotFoundException(String invoiceNumber) {
        super("sales invoice with number " + invoiceNumber + " not found.");
    }

    public SalesInvoiceNotFoundException(Long id) {
        super("sales invoice with id " + id + " not found.");
    }
}