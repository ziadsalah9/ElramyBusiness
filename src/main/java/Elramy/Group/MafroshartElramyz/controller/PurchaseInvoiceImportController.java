package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.PurchaseInvoiceResponse;
import Elramy.Group.MafroshartElramyz.services.PurchaseInvoiceImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-invoices")
@RequiredArgsConstructor
public class PurchaseInvoiceImportController {

    private final PurchaseInvoiceImportService
            purchaseInvoiceImportService;


    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<List<PurchaseInvoiceResponse>> importFile(

            @RequestParam("file")
            MultipartFile file

    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        purchaseInvoiceImportService
                                .importFile(file)
                );
    }
}