package Elramy.Group.MafroshartElramyz.services;



import Elramy.Group.MafroshartElramyz.enums.salesInvoice.SalesInvoiceRequest;
import Elramy.Group.MafroshartElramyz.enums.salesInvoice.SalesInvoiceResponse;
import Elramy.Group.MafroshartElramyz.models.SalesInvoice;

import java.util.List;

public interface SalesInvoiceService {

    SalesInvoiceResponse create(
            SalesInvoiceRequest request
    );

    SalesInvoiceResponse getById(Long id);

    List<SalesInvoiceResponse> getAll();

    List<SalesInvoiceResponse> getByBranch(
            Long branchId
    );

    List<SalesInvoiceResponse> getByCashier(
            Long cashierId
    );

    List<SalesInvoiceResponse>getByPhone(String phone) ;

}