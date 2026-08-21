package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;

import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.CreatePurchaseInvoiceRequest;
import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.PurchaseInvoiceImportRow;
import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.PurchaseInvoiceItemRequest;
import Elramy.Group.MafroshartElramyz.enums.purchaseInvoice.PurchaseInvoiceResponse;
import Elramy.Group.MafroshartElramyz.exception.DuplicatePurchaseInvoiceException;
import Elramy.Group.MafroshartElramyz.exception.PurchaseInvoiceNotFoundException;
import Elramy.Group.MafroshartElramyz.mapping.PurchaseInvoiceMapper;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.PurchaseInvoice;
import Elramy.Group.MafroshartElramyz.models.PurchaseInvoiceItem;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import Elramy.Group.MafroshartElramyz.repository.PurchaseInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseInvoiceServiceImpl
        implements PurchaseInvoiceService {

    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;

    private final PurchaseInvoiceMapper purchaseInvoiceMapper;
    private final StockService stockService;
    private final UserService userService;


    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public PurchaseInvoiceResponse create(
            CreatePurchaseInvoiceRequest request) {

        // -----------------------------------------------------
        // 1. Validate invoice number
        // -----------------------------------------------------

        if (purchaseInvoiceRepository
                .existsByInvoiceNumber(request.invoiceNumber())) {

            throw new DuplicatePurchaseInvoiceException(
                    request.invoiceNumber()
            );
        }


        // -----------------------------------------------------
        // 2. Get branch
        // -----------------------------------------------------

        Branch branch = branchRepository
                .findById(request.branchId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Branch not found with id: "
                                        + request.branchId()
                        )
                );


        var username = userService.getCurrentUser();
        userService.validateBranchAccess(request.branchId());

        // -----------------------------------------------------
        // 3. Create invoice
        // -----------------------------------------------------

        PurchaseInvoice invoice =
                PurchaseInvoice.builder()
                        .invoiceNumber(request.invoiceNumber())
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
                        .total(BigDecimal.ZERO)
                        .createdBy(username)
                        .build();


        // -----------------------------------------------------
        // 4. Create items
        // -----------------------------------------------------

        BigDecimal itemsTotal = BigDecimal.ZERO;

        for (PurchaseInvoiceItemRequest itemRequest
                : request.items()) {

            Product product = productRepository
                    .findById(itemRequest.productId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Product not found with id: "
                                            + itemRequest.productId()
                            )
                    );


            BigDecimal discount =
                    itemRequest.discount() != null
                            ? itemRequest.discount()
                            : BigDecimal.ZERO;


            BigDecimal itemTotal =
                    itemRequest.unitPrice()
                            .multiply(
                                    BigDecimal.valueOf(
                                            itemRequest.quantity()
                                    )
                            )
                            .subtract(discount);


            if (itemTotal.compareTo(BigDecimal.ZERO) < 0) {

                throw new IllegalArgumentException(
                        "Item total cannot be negative."
                );
            }


            PurchaseInvoiceItem item =
                    PurchaseInvoiceItem.builder()
                            .invoice(invoice)
                            .product(product)
                            .quantity(itemRequest.quantity())
                            .unitPrice(itemRequest.unitPrice())
                            .discount(discount)
                            .total(itemTotal)
                            .build();


            invoice.getItems().add(item);

            itemsTotal = itemsTotal.add(itemTotal);
        }


        // -----------------------------------------------------
        // 5. Calculate invoice total
        // -----------------------------------------------------

        BigDecimal invoiceDiscount =
                request.discount() != null
                        ? request.discount()
                        : BigDecimal.ZERO;


        BigDecimal total =
                itemsTotal.subtract(invoiceDiscount);


        if (total.compareTo(BigDecimal.ZERO) < 0) {

            throw new IllegalArgumentException(
                    "Invoice total cannot be negative."
            );
        }


        // -----------------------------------------------------
        // 6. Validate paid
        // -----------------------------------------------------

        BigDecimal paid =
                request.paid() != null
                        ? request.paid()
                        : BigDecimal.ZERO;


        if (paid.compareTo(total) > 0) {

            throw new IllegalArgumentException(
                    "Paid amount cannot be greater than invoice total."
            );
        }


        invoice.setTotal(total);


        // -----------------------------------------------------
        // 7. Save invoice
        // -----------------------------------------------------

        purchaseInvoiceRepository.save(invoice);


        // -----------------------------------------------------
        // 8. Increase stock
        // -----------------------------------------------------

        for (PurchaseInvoiceItem item : invoice.getItems()) {

            stockService.increaseStock(

                    item.getProduct().getId(),

                    branch.getId(),

                    item.getQuantity(),

                    TransactionType.STOCK_IN,

                    item.getUnitPrice(),

                    invoice.getId(),



                    "PURCHASE_INVOICE",

                    "Purchase invoice: "
                            + invoice.getInvoiceNumber()
            );
        }


        // -----------------------------------------------------
        // 9. Return response
        // -----------------------------------------------------

        return purchaseInvoiceMapper.toResponse(invoice);
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public PurchaseInvoiceResponse getById(Long id) {

        PurchaseInvoice invoice =
                purchaseInvoiceRepository.findById(id)
                        .orElseThrow(() ->
                                new PurchaseInvoiceNotFoundException(id)
                        );

        return purchaseInvoiceMapper.toResponse(invoice);
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseInvoiceResponse> getAll() {

        return purchaseInvoiceRepository
                .findAll()
                .stream()
                .map(purchaseInvoiceMapper::toResponse)
                .toList();
    }


    // =========================================================
    // GET BY INVOICE NUMBER
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public PurchaseInvoiceResponse getByInvoiceNumber(
            String invoiceNumber) {

        PurchaseInvoice invoice =
                purchaseInvoiceRepository
                        .findByInvoiceNumber(invoiceNumber)
                        .orElseThrow(() ->
                                new PurchaseInvoiceNotFoundException(
                                        invoiceNumber
                                )
                        );

        return purchaseInvoiceMapper.toResponse(invoice);
    }


    @Override
    public List<PurchaseInvoiceResponse> importInvoices(
            List<PurchaseInvoiceImportRow> rows) {

        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "No purchase invoice data found."
            );
        }

        /*
         * كل Invoice Number ممكن يكون له أكثر من Row
         *
         * مثال:
         *
         * PUR-001 -> Product A
         * PUR-001 -> Product B
         * PUR-001 -> Product C
         *
         * هنحولهم لفاتورة واحدة.
         */
        Map<String, List<PurchaseInvoiceImportRow>> groupedInvoices =
                rows.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.groupingBy(
                                PurchaseInvoiceImportRow::invoiceNumber
                        ));

        List<PurchaseInvoiceResponse> responses = new java.util.ArrayList<>();

        for (Map.Entry<String, List<PurchaseInvoiceImportRow>> entry
                : groupedInvoices.entrySet()) {

            String invoiceNumber = entry.getKey();

            List<PurchaseInvoiceImportRow> invoiceRows =
                    entry.getValue();

            // =====================================================
            // 1. Validate invoice number
            // =====================================================

            if (invoiceNumber == null || invoiceNumber.isBlank()) {
                throw new IllegalArgumentException(
                        "Invoice number cannot be empty."
                );
            }

            if (purchaseInvoiceRepository
                    .existsByInvoiceNumber(invoiceNumber)) {

                throw new DuplicatePurchaseInvoiceException(
                        invoiceNumber
                );
            }

            // =====================================================
            // 2. Get branch
            // =====================================================

            Long branchId = invoiceRows.get(0).branchId();

            if (branchId == null) {
                throw new IllegalArgumentException(
                        "Branch ID is required for invoice: "
                                + invoiceNumber
                );
            }

            // نتأكد إن كل rows لنفس الفاتورة على نفس الفرع
            boolean differentBranch =
                    invoiceRows.stream()
                            .anyMatch(row ->
                                    !Objects.equals(
                                            row.branchId(),
                                            branchId
                                    )
                            );

            if (differentBranch) {
                throw new IllegalArgumentException(
                        "Invoice " + invoiceNumber
                                + " contains multiple branches."
                );
            }

            Branch branch = branchRepository
                    .findById(branchId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Branch not found with id: "
                                            + branchId
                            )
                    );

            // =====================================================
            // 3. Create invoice
            // =====================================================

            PurchaseInvoice invoice =
                    PurchaseInvoice.builder()
                            .invoiceNumber(invoiceNumber)
                            .branch(branch)
                            .discount(BigDecimal.ZERO)
                            .paid(BigDecimal.ZERO)
                            .paymentMethod(
                                    invoiceRows.get(0).paymentMethod()
                            )
                            .notes(invoiceRows.get(0).notes())
                            .total(BigDecimal.ZERO)
                            .build();

            // =====================================================
            // 4. Create invoice items
            // =====================================================

            BigDecimal itemsTotal = BigDecimal.ZERO;

            for (PurchaseInvoiceImportRow row : invoiceRows) {

                if (row.productCode() == null
                        || row.productCode().isBlank()) {

                    throw new IllegalArgumentException(
                            "Product code is required in invoice: "
                                    + invoiceNumber
                    );
                }

                if (row.quantity() == null
                        || row.quantity() <= 0) {

                    throw new IllegalArgumentException(
                            "Quantity must be greater than zero "
                                    + "for product: "
                                    + row.productCode()
                    );
                }

                if (row.unitPrice() == null
                        || row.unitPrice().compareTo(BigDecimal.ZERO) < 0) {

                    throw new IllegalArgumentException(
                            "Unit price cannot be negative "
                                    + "for product: "
                                    + row.productCode()
                    );
                }

                // ---------------------------------------------
                // Find product by CODE
                // ---------------------------------------------

                Product product = productRepository
                        .findByCode(row.productCode())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Product not found with code: "
                                                + row.productCode()
                                )
                        );

                // ---------------------------------------------
                // Discount
                // ---------------------------------------------

                BigDecimal discount =
                        row.discount() != null
                                ? row.discount()
                                : BigDecimal.ZERO;

                if (discount.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException(
                            "Discount cannot be negative."
                    );
                }

                // ---------------------------------------------
                // Item total
                // ---------------------------------------------

                BigDecimal itemTotal =
                        row.unitPrice()
                                .multiply(
                                        BigDecimal.valueOf(
                                                row.quantity()
                                        )
                                )
                                .subtract(discount);

                if (itemTotal.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException(
                            "Item total cannot be negative "
                                    + "for product: "
                                    + row.productCode()
                    );
                }

                PurchaseInvoiceItem item =
                        PurchaseInvoiceItem.builder()
                                .invoice(invoice)
                                .product(product)
                                .quantity(row.quantity())
                                .unitPrice(row.unitPrice())
                                .discount(discount)
                                .total(itemTotal)
                                .build();

                invoice.getItems().add(item);

                itemsTotal =
                        itemsTotal.add(itemTotal);
            }

            // =====================================================
            // 5. Calculate invoice total
            // =====================================================

            BigDecimal invoiceDiscount =
                    invoice.getDiscount() != null
                            ? invoice.getDiscount()
                            : BigDecimal.ZERO;

            BigDecimal total =
                    itemsTotal.subtract(invoiceDiscount);

            if (total.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Invoice total cannot be negative: "
                                + invoiceNumber
                );
            }

            invoice.setTotal(total);

            // =====================================================
            // 6. Save invoice FIRST
            // =====================================================
            //
            // مهم جدًا:
            // لازم نحفظ invoice الأول علشان يتولد ID
            // وبعدها نستخدم invoice.getId()
            // كـ referenceId في StockTransaction
            //

            purchaseInvoiceRepository.save(invoice);

            // =====================================================
            // 7. Increase stock
            // =====================================================

            for (PurchaseInvoiceItem item : invoice.getItems()) {

                stockService.increaseStock(

                        item.getProduct().getId(),

                        branch.getId(),

                        item.getQuantity(),

                        TransactionType.STOCK_IN,

                        item.getUnitPrice(),

                        invoice.getId(),

                        "PURCHASE_INVOICE",

                        "Imported purchase invoice: "
                                + invoice.getInvoiceNumber()
                );
            }

            // =====================================================
            // 8. Response
            // =====================================================

            responses.add(
                    purchaseInvoiceMapper.toResponse(invoice)
            );
        }

        return responses;
    }


}