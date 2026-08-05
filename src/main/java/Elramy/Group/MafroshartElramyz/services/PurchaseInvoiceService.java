package Elramy.Group.MafroshartElramyz.services;


import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.CreatePurchaseInvoiceRequest;
import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.PurchaseInvoiceImportRow;
import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.PurchaseInvoiceResponse;

import java.util.List;

public interface PurchaseInvoiceService {

    PurchaseInvoiceResponse create(
            CreatePurchaseInvoiceRequest request
    );

    PurchaseInvoiceResponse getById(Long id);

    List<PurchaseInvoiceResponse> getAll();

    PurchaseInvoiceResponse getByInvoiceNumber(
            String invoiceNumber
    );
    List<PurchaseInvoiceResponse> importInvoices(
            List<PurchaseInvoiceImportRow> rows
    );

}