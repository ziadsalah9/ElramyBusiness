package Elramy.Group.MafroshartElramyz.services;


import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.enums.Role;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceItemRequest;
import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceRequest;
import Elramy.Group.MafroshartElramyz.dto.salesInvoice.SalesInvoiceResponse;
import Elramy.Group.MafroshartElramyz.exception.BranchAccessDeniedException;
import Elramy.Group.MafroshartElramyz.exception.BranchNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.CustomerPhoneNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.PriceNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.ProductNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.SalesInvoiceNotFoundException;
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
    private final ProductVariantRepository variantRepository;
    private final BranchRepository branchRepository;

    private final StockService stockService;
    private final SalesInvoiceMapper salesInvoiceMapper;
    private final CustomerRepository customerRepository;
    private final UserService userService;
    private final ActivityLogService activityLog;
    private final Elramy.Group.MafroshartElramyz.settings.StoreSettingsService storeSettings;


    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public SalesInvoiceResponse create(
            SalesInvoiceRequest request) {

        // =========================================================
        // GET BRANCH
        // =========================================================

        Branch branch = branchRepository
                .findById(request.branchId())
                .orElseThrow(() ->
                        new BranchNotFoundException(
                                request.branchId()
                        )
                );

        if (branch.getActive() != null
                && !branch.getActive()) {

            throw new IllegalArgumentException(
                    "Cannot sell from an inactive branch."
            );
        }

        User currentUser =
                userService.getCurrentUser();

        if (currentUser.getRole() != Role.ADMIN) {

            if (currentUser.getBranch() == null) {
                throw new BranchAccessDeniedException(
                        "Current user is not assigned to a branch"
                );
            }

            if (!currentUser.getBranch().getId()
                    .equals(branch.getId())) {

                throw new BranchAccessDeniedException(
                        "You cannot create an invoice for another branch"
                );
            }
        }
        // =========================================================
        // CUSTOMER
        // =========================================================

        // Required by validation; matched by phone, created on first sale.
        String phone = request.customer().phone().trim();
        Customer customer = customerRepository.findByPhone(phone).orElse(null);
        if (customer == null) {
            customer = customerRepository.save(Customer.builder()
                    .name(request.customer().name().trim())
                    .phone(phone)
                    .address(request.customer().address())
                    .active(true)
                    .build());
            activityLog.record(ActivityEntity.CUSTOMER, ActivityAction.CREATE, customer.getId(),
                    customer.getName() + " (" + phone + ")", "إضافة عميل من فاتورة بيع «" + customer.getName() + "»",
                    Changes.of().set("الاسم", customer.getName()).set("الهاتف", phone).set("العنوان", customer.getAddress()));
        } else if (Boolean.FALSE.equals(customer.getActive())) {
            // A hidden (soft-deleted) customer who buys again is back.
            customer.setActive(true);
            customerRepository.save(customer);
            activityLog.record(ActivityEntity.CUSTOMER, ActivityAction.STATUS, customer.getId(),
                    customer.getName() + " (" + phone + ")", "استعادة العميل «" + customer.getName() + "» عند البيع له");
        }


        // =========================================================
        // PAID
        // =========================================================

        BigDecimal paid =
                request.paid() != null
                        ? request.paid()
                        : BigDecimal.ZERO;


        // =========================================================
        // INVOICE DISCOUNT
        // =========================================================

        BigDecimal invoiceDiscount =
                request.discount() != null
                        ? request.discount()
                        : BigDecimal.ZERO;


        // =========================================================
        // CREATE INVOICE
        // =========================================================

        SalesInvoice invoice =
                SalesInvoice.builder()
                        .invoiceNumber(generateInvoiceNumber())
                        .branch(branch)
                        .customer(customer)
                        .cashier(currentUser)
                        .discount(invoiceDiscount)
                        .paid(paid)
                        .paymentMethod(request.paymentMethod())
                        .notes(request.notes())
                        .build();


        // =========================================================
        // CREATE ITEMS
        // =========================================================

        List<SalesInvoiceItem> items =
                new ArrayList<>();

        BigDecimal subtotal = BigDecimal.ZERO;


        for (SalesInvoiceItemRequest itemRequest
                : request.items()) {

            // The exact colour/size being sold (V11).
            ProductVariant variant =
                    variantRepository
                            .findById(itemRequest.variantId())
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Product variant " + itemRequest.variantId() + " not found."
                                    )
                            );
            Product product = variant.getProduct();

            if (Boolean.FALSE.equals(variant.getActive())) {
                throw new IllegalArgumentException(
                        "هذا اللون/المقاس غير نشط: " + product.getName() + " (" + variant.label() + ")"
                );
            }

            // A soft-deleted product must not be sellable.
            if (Boolean.FALSE.equals(product.getActive())) {

                throw new IllegalArgumentException(
                        "Product is not active: "
                                + product.getName()
                );
            }


            // =====================================================
            // GET CURRENT SELLING PRICE
            // =====================================================

            // The current price lives on the product (see V6).
            if (product.getSellingPrice() == null) {
                throw new PriceNotFoundException(product.getId());
            }

            BigDecimal unitPrice =
                    product.getSellingPrice();


            // =====================================================
            // ITEM DISCOUNT
            // =====================================================

            BigDecimal itemDiscount =
                    itemRequest.discount() != null
                            ? itemRequest.discount()
                            : BigDecimal.ZERO;


            // =====================================================
            // CALCULATE ITEM TOTAL
            // =====================================================

            BigDecimal itemTotal =
                    unitPrice
                            .multiply(
                                    BigDecimal.valueOf(
                                            itemRequest.quantity()
                                    )
                            )
                            .subtract(itemDiscount)
                            // Money is stored to 2 decimals consistently.
                            .setScale(2, java.math.RoundingMode.HALF_UP);


            if (itemTotal.compareTo(BigDecimal.ZERO) < 0) {

                throw new IllegalArgumentException(
                        "Item discount cannot be greater than item total"
                );
            }


            // =====================================================
            // CREATE ITEM
            // =====================================================

            SalesInvoiceItem item =
                    SalesInvoiceItem.builder()
                            .invoice(invoice)
                            .product(product)
                            .variant(variant)
                            .quantity(itemRequest.quantity())
                            .unitPrice(unitPrice)
                            .discount(itemDiscount)
                            .total(itemTotal)
                            .build();


            items.add(item);

            subtotal = subtotal.add(itemTotal);
        }


        // =========================================================
        // CALCULATE INVOICE TOTAL
        // =========================================================

        BigDecimal total =
                subtotal.subtract(invoiceDiscount)
                        .setScale(2, java.math.RoundingMode.HALF_UP);


        if (total.compareTo(BigDecimal.ZERO) < 0) {

            throw new IllegalArgumentException(
                    "Invoice discount cannot be greater than subtotal"
            );
        }


        // =========================================================
        // VALIDATE PAID
        // =========================================================

        if (paid.compareTo(total) > 0) {

            throw new IllegalArgumentException(
                    "Paid amount cannot be greater than invoice total"
            );
        }


        // =========================================================
        // EMPLOYEE DISCOUNT LIMIT
        // =========================================================

        // Line and invoice discounts together, as a share of the invoice
        // before any discount. Admins have no limit.
        if (currentUser.getRole() != Role.ADMIN) {
            BigDecimal gross = items.stream()
                    .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal discounts = gross.subtract(total);
            BigDecimal maxPercent = storeSettings.employeeMaxDiscountPercent();
            BigDecimal allowed = gross.multiply(maxPercent).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            if (discounts.compareTo(allowed) > 0) {
                throw new IllegalArgumentException("الخصم (" + discounts.stripTrailingZeros().toPlainString()
                        + " ج.م) أكبر من المسموح للموظف (" + maxPercent.stripTrailingZeros().toPlainString()
                        + "% = " + allowed.stripTrailingZeros().toPlainString() + " ج.م). اطلب من المدير.");
            }
        }


        // =========================================================
        // CASH PART (for the cash closing)
        // =========================================================

        BigDecimal cashAmount;
        if (request.paymentMethod() == PaymentMethod.CASH) {
            cashAmount = paid;
        } else if (request.paymentMethod() == PaymentMethod.MIXED) {
            if (request.cashAmount() == null) {
                throw new IllegalArgumentException("اكتب الجزء المدفوع نقداً من الدفع المختلط.");
            }
            cashAmount = request.cashAmount().setScale(2, java.math.RoundingMode.HALF_UP);
            if (cashAmount.compareTo(paid) > 0) {
                throw new IllegalArgumentException("الجزء النقدي لا يمكن أن يزيد عن المبلغ المدفوع.");
            }
        } else {
            cashAmount = BigDecimal.ZERO;
        }
        invoice.setCashAmount(cashAmount);


        // =========================================================
        // SET ITEMS + TOTAL
        // =========================================================

        invoice.setItems(items);
        invoice.setTotal(total);


        // =========================================================
        // SAVE INVOICE FIRST
        // =========================================================

        salesInvoiceRepository.save(invoice);


        // =========================================================
        // DECREASE STOCK
        // =========================================================

        // Rows are locked in id order so two invoices sharing variants
        // cannot deadlock each other (X locks A then B, Y locks B then A).
        for (SalesInvoiceItem item : items.stream()
                .sorted(java.util.Comparator.comparing((SalesInvoiceItem i) -> i.getVariant().getId()))
                .toList()) {

            stockService.decreaseStock(

                    item.getVariant().getId(),

                    item.getQuantity(),

                    TransactionType.SALE,

                    item.getUnitPrice(),

                    invoice.getId(),

                    "SALES_INVOICE",

                    "Sales invoice: "
                            + invoice.getInvoiceNumber()
            );
        }


        // =========================================================
        // RETURN RESPONSE
        // =========================================================

        activityLog.record(ActivityEntity.SALES_INVOICE, ActivityAction.CREATE, invoice.getId(), invoice.getInvoiceNumber(),
                "فاتورة بيع " + invoice.getInvoiceNumber()
                        + (invoice.getCustomer() != null ? " للعميل «" + invoice.getCustomer().getName() + "»" : "")
                        + " بإجمالي " + invoice.getTotal().stripTrailingZeros().toPlainString() + " ج.م",
                invoiceLines(invoice.getItems().stream()
                        .map(i -> lineText(i.getProduct().getName(), i.getVariant(), i.getQuantity(), i.getTotal())).toList())
                        .set("الخصم", invoice.getDiscount())
                        .set("المدفوع", invoice.getPaid())
                        .set("المتبقي", invoice.getTotal().subtract(invoice.getPaid()))
                        .set("طريقة الدفع", invoice.getPaymentMethod()));

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
                                new SalesInvoiceNotFoundException(id)
                        );

        // EMPLOYEE may only read invoices of their own branch.
        userService.validateBranchAccess(
                invoice.getBranch().getId()
        );

        return salesInvoiceMapper.toResponse(invoice);
    }



    @Transactional(readOnly = true)
    @Override
    public List<SalesInvoiceResponse> getByPhone(String phone) {

        var customer = customerRepository.findByPhone(phone == null ? null : phone.trim()).orElseThrow(() ->
                new CustomerPhoneNotFoundException(
                        phone
                )
        );

        var id = customer.getId();

        Long branchId =
                userService.getEffectiveBranchId(null);

        return salesInvoiceRepository
                .findByCustomerId(
                        id
                )
                .stream()
                // EMPLOYEE sees only their branch's invoices for the customer.
                .filter(invoice ->
                        branchId == null
                                || invoice.getBranch().getId()
                                .equals(branchId))
                .map(salesInvoiceMapper::toResponse)
                .toList();
    }

    // =========================================================
    // GET BY INVOICE NUMBER
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public SalesInvoiceResponse getByInvoiceNumber(
            String invoiceNumber) {

        var invoice =
                salesInvoiceRepository
                        .findByInvoiceNumber(invoiceNumber)
                        .orElseThrow(() ->
                                new SalesInvoiceNotFoundException(
                                        invoiceNumber
                                )
                        );

        userService.validateBranchAccess(
                invoice.getBranch().getId()
        );

        return salesInvoiceMapper.toResponse(invoice);
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceResponse> getAll() {
        return getAll(null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceResponse> getAll(java.time.LocalDate from, java.time.LocalDate to) {
        Long branchId = userService.getEffectiveBranchId(null);
        Elramy.Group.MafroshartElramyz.util.DateRange range = Elramy.Group.MafroshartElramyz.util.DateRange.of(from, to);
        return salesInvoiceRepository.findInRange(branchId, range.from(), range.to())
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

        userService.validateBranchAccess(branchId);

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

        User currentUser = userService.getCurrentUser();

        // An EMPLOYEE may only look at their own invoices.
        if (currentUser.getRole() != Role.ADMIN
                && !currentUser.getId().equals(cashierId)) {

            throw new BranchAccessDeniedException(
                    "You cannot view another cashier's invoices"
            );
        }

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

    static Changes invoiceLines(List<String> lines) {
        Changes c = Changes.of();
        for (int i = 0; i < lines.size(); i++) c.set("بند " + (i + 1), lines.get(i));
        return c;
    }

    static String lineText(String product, ProductVariant variant, Integer qty, BigDecimal total) {
        String v = variant != null && !variant.label().equals("بدون لون أو مقاس") ? " (" + variant.label() + ")" : "";
        return product + v + " × " + qty + " = " + (total != null ? total.stripTrailingZeros().toPlainString() : "-");
    }
}
