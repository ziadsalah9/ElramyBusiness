package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.dto.purchaseInvoice.PurchaseInvoiceResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PurchaseInvoiceImportService {

    List<PurchaseInvoiceResponse> importFile(
            MultipartFile file
    );
}