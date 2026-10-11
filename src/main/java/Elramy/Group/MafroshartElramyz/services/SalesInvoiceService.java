package Elramy.Group.MafroshartElramyz.services;



import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceRequest;
import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceResponse;
import Elramy.Group.MafroshartElramyz.models.SalesInvoice;

import java.util.List;

public interface SalesInvoiceService {

    SalesInvoiceResponse create(
            SalesInvoiceRequest request
    );

    SalesInvoiceResponse getById(Long id);

    SalesInvoiceResponse getByInvoiceNumber(
            String invoiceNumber
    );

    List<SalesInvoiceResponse> getAll();

    /** Newest first; null dates = open ends. Employees only see their branch. */
    List<SalesInvoiceResponse> getAll(java.time.LocalDate from, java.time.LocalDate to);

    List<SalesInvoiceResponse> getByBranch(
            Long branchId
    );

    List<SalesInvoiceResponse> getByCashier(
            Long cashierId
    );

    List<SalesInvoiceResponse>getByPhone(String phone) ;

}