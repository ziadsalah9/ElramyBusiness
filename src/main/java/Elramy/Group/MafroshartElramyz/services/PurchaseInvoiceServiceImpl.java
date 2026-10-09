package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;

import Elramy.Group.MafroshartElramyz.dto.purchaseInvoice.CreatePurchaseInvoiceRequest;
import Elramy.Group.MafroshartElramyz.dto.purchaseInvoice.PurchaseInvoiceImportRow;
import Elramy.Group.MafroshartElramyz.dto.purchaseInvoice.PurchaseInvoiceItemRequest;
import Elramy.Group.MafroshartElramyz.dto.purchaseInvoice.PurchaseInvoiceResponse;
import Elramy.Group.MafroshartElramyz.exception.BranchNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.DuplicatePurchaseInvoiceException;
import Elramy.Group.MafroshartElramyz.exception.ProductNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.PurchaseInvoiceNotFoundException;
import Elramy.Group.MafroshartElramyz.mapping.PurchaseInvoiceMapper;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.PurchaseInvoice;
import Elramy.Group.MafroshartElramyz.models.PurchaseInvoiceItem;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
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
    private final ProductVariantRepository variantRepository;
    private final BranchRepository branchRepository;

    private final PurchaseInvoiceMapper purchaseInvoiceMapper;
    private final StockService stockService;
    private final UserService userService;
    private final ActivityLogService activityLog;
    private final Elramy.Group.MafroshartElramyz.supplier.SupplierService supplierService;


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
                        new BranchNotFoundException(
                                request.branchId()
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
                        .supplier(supplierService.activeSupplier(request.supplierId()))
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

            ProductVariant variant = activeVariant(itemRequest.variantId());
            Product product = variant.getProduct();


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
                            .subtract(discount)
                            // Money is stored to 2 decimals, like sales.
                            .setScale(2, java.math.RoundingMode.HALF_UP);


            if (itemTotal.compareTo(BigDecimal.ZERO) < 0) {

                throw new IllegalArgumentException(
                        "Item total cannot be negative."
                );
            }


            PurchaseInvoiceItem item =
                    PurchaseInvoiceItem.builder()
                            .invoice(invoice)
                            .product(product)
                            .variant(variant)
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
                itemsTotal.subtract(invoiceDiscount).setScale(2, java.math.RoundingMode.HALF_UP);


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

        // Locked in id order: no deadlock with concurrent invoices.
        for (PurchaseInvoiceItem item : invoice.getItems().stream()
                .sorted(java.util.Comparator.comparing((PurchaseInvoiceItem i) -> i.getVariant().getId()))
                .toList()) {

            stockService.increaseStock(

                        item.getVariant().getId(),

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

        logPurchase(invoice, false);
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
        return getAll(null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseInvoiceResponse> getAll(java.time.LocalDate from, java.time.LocalDate to) {
        Elramy.Group.MafroshartElramyz.util.DateRange range = Elramy.Group.MafroshartElramyz.util.DateRange.of(from, to);
        return purchaseInvoiceRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(range.from(), range.to())
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
    public List<PurchaseInvoiceResponse> importInvoices(List<PurchaseInvoiceImportRow> rows, Long supplierId) {

        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "No purchase invoice data found."
            );
        }

        Elramy.Group.MafroshartElramyz.supplier.Supplier supplier = supplierService.activeSupplier(supplierId);

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
                        // groupingBy rejects a null key (500 instead of the
                        // "empty invoice number" message below); a blank key
                        // keeps those rows together so that check reports them.
                        // LinkedHashMap keeps the file's order.
                        .collect(Collectors.groupingBy(
                                r -> r.invoiceNumber() == null ? "" : r.invoiceNumber().trim(),
                                java.util.LinkedHashMap::new,
                                Collectors.toList()
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
                            new BranchNotFoundException(branchId)
                    );

            // =====================================================
            // 3. Create invoice
            // =====================================================

            PurchaseInvoice invoice =
                    PurchaseInvoice.builder()
                            .invoiceNumber(invoiceNumber)
                            .branch(branch)
                            .supplier(supplier)
                            .discount(BigDecimal.ZERO)
                            .paid(BigDecimal.ZERO)
                            .paymentMethod(
                                    invoiceRows.get(0).paymentMethod()
                            )
                            .notes(invoiceRows.get(0).notes())
                            .total(BigDecimal.ZERO)
                            .createdBy(userService.getCurrentUser())
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

                ProductVariant variant = variantByCode(row.productCode());
                Product product = variant.getProduct();

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
                                .variant(variant)
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

            // Locked in id order: no deadlock with concurrent invoices.
            for (PurchaseInvoiceItem item : invoice.getItems().stream()
                    .sorted(java.util.Comparator.comparing((PurchaseInvoiceItem i) -> i.getVariant().getId()))
                    .toList()) {

                stockService.increaseStock(

                        item.getVariant().getId(),

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

            logPurchase(invoice, true);
            responses.add(
                    purchaseInvoiceMapper.toResponse(invoice)
            );
        }

        return responses;
    }



    // =========================================================
    // VARIANT HELPERS
    // =========================================================

    /** The variant being bought; must exist and be active. */
    private ProductVariant activeVariant(Long variantId) {
        ProductVariant v = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant " + variantId + " not found."));
        if (Boolean.FALSE.equals(v.getActive())) {
            throw new IllegalArgumentException("هذا اللون/المقاس غير نشط: " + v.getProduct().getName() + " (" + v.label() + ")");
        }
        return v;
    }

    /**
     * Excel import: the code is a variant code (PRD-XXXX-2) or, for a
     * product with exactly one active variant, the product code.
     */
    private ProductVariant variantByCode(String code) {
        String c = code.trim();
        var byVariant = variantRepository.findByScanCode(c);
        if (byVariant.isPresent()) return activeVariant(byVariant.get().getId());
        Product product = productRepository.findByCode(c)
                .orElseThrow(() -> new ProductNotFoundException(c));
        var active = product.activeVariants();
        if (active.size() != 1) {
            throw new IllegalArgumentException("المنتج " + c + " له أكثر من لون/مقاس؛ استخدم كود اللون/المقاس (مثل " + c + "-1).");
        }
        return active.get(0);
    }

    private void logPurchase(PurchaseInvoice invoice, boolean imported) {
        activityLog.record(ActivityEntity.PURCHASE_INVOICE, imported ? ActivityAction.IMPORT : ActivityAction.CREATE,
                invoice.getId(), invoice.getInvoiceNumber(),
                (imported ? "استيراد فاتورة شراء " : "فاتورة شراء ") + invoice.getInvoiceNumber()
                        + " بإجمالي " + invoice.getTotal().stripTrailingZeros().toPlainString() + " ج.م",
                SalesInvoiceServiceImpl.invoiceLines(invoice.getItems().stream()
                        .map(i -> SalesInvoiceServiceImpl.lineText(i.getProduct().getName(), i.getVariant(), i.getQuantity(), i.getTotal()))
                        .toList())
                        .set("الخصم", invoice.getDiscount())
                        .set("المدفوع", invoice.getPaid())
                        .set("طريقة الدفع", invoice.getPaymentMethod()));
    }
}
