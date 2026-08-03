package Elramy.Group.MafroshartElramyz.services;


import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.enums.salesInvoice.SalesInvoiceItemRequest;
import Elramy.Group.MafroshartElramyz.enums.salesInvoice.SalesInvoiceRequest;
import Elramy.Group.MafroshartElramyz.enums.salesInvoice.SalesInvoiceResponse;
import Elramy.Group.MafroshartElramyz.exception.ProductNotFoundException;
import Elramy.Group.MafroshartElramyz.mapping.SalesInvoiceMapper;
import Elramy.Group.MafroshartElramyz.models.*;
import Elramy.Group.MafroshartElramyz.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SalesInvoiceServiceImpl
        implements SalesInvoiceService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final BranchRepository branchRepository;

    private final StockService stockService;
    private final SalesInvoiceMapper salesInvoiceMapper;


    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public SalesInvoiceResponse create(
            SalesInvoiceRequest request) {

        // -----------------------------------------------------
        // 1. GET BRANCH
        // -----------------------------------------------------

        Branch branch = branchRepository
                .findById(request.branchId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Branch not found with id: "
                                        + request.branchId()
                        )
                );


        // -----------------------------------------------------
        // 2. CREATE INVOICE
        // -----------------------------------------------------

        SalesInvoice invoice = SalesInvoice.builder()
                .invoiceNumber(generateInvoiceNumber())
                .branch(branch)
                .discount(
                        request.discount() != null
                                ? request.discount()
                                : BigDecimal.ZERO
                )
                .paid(
                        request.paid() != null
                                ? request.paid()
                                : BigDecimal.ZERO
                )
                .paymentMethod(request.paymentMethod())
                .notes(request.notes())
                .build();


        List<SalesInvoiceItem> items =
                new ArrayList<>();

        BigDecimal subtotal = BigDecimal.ZERO;


        // -----------------------------------------------------
        // 3. CREATE ITEMS
        // -----------------------------------------------------

        for (SalesInvoiceItemRequest itemRequest
                : request.items()) {

            Product product = productRepository
                    .findById(itemRequest.productId())
                    .orElseThrow(() ->
                            new ProductNotFoundException(
                                    itemRequest.productId()
                            )
                    );


            // -------------------------------------------------
            // GET CURRENT PRICE
            // -------------------------------------------------

            ProductPrice currentPrice =
                    productPriceRepository
                            .findByProductIdAndActiveTrue(
                                    product.getId()
                            )
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Current price not found for product: "
                                                    + product.getId()
                                    )
                            );


            BigDecimal unitPrice =
                    currentPrice.getSellingPrice();


            // -------------------------------------------------
            // DISCOUNT
            // -------------------------------------------------

            BigDecimal itemDiscount =
                    itemRequest.discount() != null
                            ? itemRequest.discount()
                            : BigDecimal.ZERO;


            // -------------------------------------------------
            // CALCULATE ITEM TOTAL
            // -------------------------------------------------

            BigDecimal itemSubtotal =
                    unitPrice.multiply(
                            BigDecimal.valueOf(
                                    itemRequest.quantity()
                            )
                    );

            BigDecimal itemTotal =
                    itemSubtotal.subtract(itemDiscount);


            if (itemTotal.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Item discount cannot be greater than item total"
                );
            }


            // -------------------------------------------------
            // CREATE ITEM
            // -------------------------------------------------

            SalesInvoiceItem item =
                    SalesInvoiceItem.builder()
                            .invoice(invoice)
                            .product(product)
                            .quantity(itemRequest.quantity())
                            .unitPrice(unitPrice)
                            .discount(itemDiscount)
                            .total(itemTotal)
                            .build();

            items.add(item);

            subtotal = subtotal.add(itemTotal);
        }


        invoice.setItems(items);


        // -----------------------------------------------------
        // 4. INVOICE DISCOUNT
        // -----------------------------------------------------

        BigDecimal invoiceDiscount =
                request.discount() != null
                        ? request.discount()
                        : BigDecimal.ZERO;


        BigDecimal total =
                subtotal.subtract(invoiceDiscount);


        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Invoice discount cannot be greater than invoice total"
            );
        }


        invoice.setTotal(total);


        // -----------------------------------------------------
        // 5. VALIDATE PAID
        // -----------------------------------------------------

        BigDecimal paid =
                request.paid() != null
                        ? request.paid()
                        : BigDecimal.ZERO;

        if (paid.compareTo(total) > 0) {
            throw new IllegalArgumentException(
                    "Paid amount cannot be greater than invoice total"
            );
        }


        // -----------------------------------------------------
        // 6. SAVE INVOICE FIRST
        // -----------------------------------------------------

        salesInvoiceRepository.save(invoice);


        // -----------------------------------------------------
        // 7. DECREASE STOCK
        // -----------------------------------------------------

        for (SalesInvoiceItem item : items) {

            stockService.decreaseStock(

                    item.getProduct().getId(),

                    branch.getId(),

                    item.getQuantity(),

                    TransactionType.SALE,

                    item.getUnitPrice(),

                    invoice.getId(),

                    "SALES_INVOICE",

                    "Sale invoice: "
                            + invoice.getInvoiceNumber()
            );
        }


        return salesInvoiceMapper.toResponse(invoice);
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public SalesInvoiceResponse getById(Long id) {

        SalesInvoice invoice =
                salesInvoiceRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Sales invoice not found with id: "
                                                + id
                                )
                        );

        return salesInvoiceMapper.toResponse(invoice);
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceResponse> getAll() {

        return salesInvoiceRepository
                .findAll()
                .stream()
                .map(salesInvoiceMapper::toResponse)
                .toList();
    }


    // =========================================================
    // GET BY BRANCH
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceResponse> getByBranch(
            Long branchId) {

        return salesInvoiceRepository
                .findByBranchIdOrderByCreatedAtDesc(
                        branchId
                )
                .stream()
                .map(salesInvoiceMapper::toResponse)
                .toList();
    }


    // =========================================================
    // GET BY CASHIER
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceResponse> getByCashier(
            Long cashierId) {

        return salesInvoiceRepository
                .findByCashierIdOrderByCreatedAtDesc(
                        cashierId
                )
                .stream()
                .map(salesInvoiceMapper::toResponse)
                .toList();
    }


    // =========================================================
    // GENERATE INVOICE NUMBER
    // =========================================================

    private String generateInvoiceNumber() {

        String invoiceNumber;

        do {

            invoiceNumber =
                    "INV-"
                            + UUID.randomUUID()
                            .toString()
                            .substring(0, 8)
                            .toUpperCase();

        } while (
                salesInvoiceRepository
                        .existsByInvoiceNumber(invoiceNumber)
        );

        return invoiceNumber;
    }
}