package Elramy.Group.MafroshartElramyz.services;


import Elramy.Group.MafroshartElramyz.dto.purchaseInvoice.CreatePurchaseInvoiceRequest;
import Elramy.Group.MafroshartElramyz.dto.purchaseInvoice.PurchaseInvoiceImportRow;
import Elramy.Group.MafroshartElramyz.dto.purchaseInvoice.PurchaseInvoiceResponse;

import java.util.List;

public interface PurchaseInvoiceService {

    PurchaseInvoiceResponse create(
            CreatePurchaseInvoiceRequest request
    );

    PurchaseInvoiceResponse getById(Long id);

    List<PurchaseInvoiceResponse> getAll();

    /** Newest first; null dates = open ends. */
    List<PurchaseInvoiceResponse> getAll(java.time.LocalDate from, java.time.LocalDate to);

    PurchaseInvoiceResponse getByInvoiceNumber(
            String invoiceNumber
    );
    List<PurchaseInvoiceResponse> importInvoices(
            List<PurchaseInvoiceImportRow> rows
    );

}