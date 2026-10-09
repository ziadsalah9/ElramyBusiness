package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.dto.product.*;
import Elramy.Group.MafroshartElramyz.exception.ConflictException;
import Elramy.Group.MafroshartElramyz.exception.PriceNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.ProductNotFoundException;
import Elramy.Group.MafroshartElramyz.mapping.ProductMapper;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import Elramy.Group.MafroshartElramyz.repository.StockTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final StockService stockService;
    private final ProductMapper productMapper;
    private final ActivityLogService activityLog;

    /** Ledger reference for stock typed in the product form. */
    static final String REFERENCE_PRODUCT_FORM = "PRODUCT_FORM";


    // =========================================================
    // CREATE PRODUCT
    // =========================================================

    @Override
    public ProductResponse create(CreateProductRequest request) {

        Product product = Product.builder()
                .code(generateProductCode())
                .name(request.name())
                .model(request.model())
                .itemType(request.itemType())
                .minimumQuantity(request.minimumQuantity() != null ? request.minimumQuantity() : 0)
                .active(true)
                .build();

        productRepository.save(product);

        // A product always has at least one variant; with no colours or
        // sizes given it gets a single "no colour / no size" variant.
        List<ProductVariantRequest> wanted = request.variants() == null || request.variants().isEmpty()
                ? List.of(new ProductVariantRequest(null, null, null))
                : request.variants();
        Changes changes = Changes.of()
                .set("الاسم", product.getName())
                .set("النوع", product.getItemType())
                .set("الموديل", product.getModel())
                .set("الحد الأدنى", product.getMinimumQuantity());
        // New product: every colour/size starts at 0, so rows without a quantity count as 0.
        syncVariants(product, ProductFieldRules.applyTotal(wanted, request.totalQuantity(), id -> 0), changes);
        activityLog.record(ActivityEntity.PRODUCT, ActivityAction.CREATE, product.getId(), productLabel(product),
                "إضافة منتج «" + product.getName() + "»", changes);

        // No price is set here: a new product stays unpriced (and cannot be
        // sold) until setPrice is called from the product form or import.
        return productMapper.toResponse(product);
    }


    // =========================================================
    // UPDATE PRODUCT
    // =========================================================

    @Override
    public ProductResponse update(
            Long id,
            UpdateProductRequest request) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        Changes changes = Changes.of()
                .add("الاسم", product.getName(), request.name())
                .add("النوع", product.getItemType(), request.itemType())
                .add("الموديل", product.getModel(), request.model());
        if (request.minimumQuantity() != null) {
            changes.add("الحد الأدنى", product.getMinimumQuantity(), request.minimumQuantity());
        }

        product.setName(request.name());
        product.setModel(request.model());
        product.setItemType(request.itemType());

        // A partial update that omits the limit must not wipe the existing one.
        if (request.minimumQuantity() != null) {
            product.setMinimumQuantity(request.minimumQuantity());
        }

        productRepository.save(product);

        if (request.variants() != null) {
            // Rows sent without a quantity keep their saved stock in the total.
            Map<Long, Integer> saved = product.getVariants().stream()
                    .filter(v -> v.getId() != null)
                    .collect(Collectors.toMap(ProductVariant::getId, ProductVariant::getQuantity));
            syncVariants(product, ProductFieldRules.applyTotal(request.variants(), request.totalQuantity(), saved::get), changes);
        }

        if (!changes.isEmpty()) {
            activityLog.record(ActivityEntity.PRODUCT, ActivityAction.UPDATE, product.getId(), productLabel(product),
                    "تعديل منتج «" + product.getName() + "»", changes);
        }

        return productMapper.toResponse(product);
    }


    // =========================================================
    // VARIANTS
    // =========================================================

    /**
     * Makes the product's active variants match {@code wanted}:
     * entries with an id update that variant, entries without one are added
     * (or reactivate an inactive variant with the same colour/size), and
     * variants left out are deactivated when they have stock or history,
     * deleted otherwise. Stock is never set here.
     */
    private void syncVariants(Product product, List<ProductVariantRequest> wanted, Changes changes) {

        List<ProductVariant> current = product.getVariants();
        Set<String> seen = new HashSet<>();
        Set<Long> keep = new HashSet<>();

        for (ProductVariantRequest w : wanted) {
            String color = resolveCatalog(ProductCatalog.COLORS, w.color(), "اللون", w.id() == null ? null : findIn(current, w.id()).getColor());
            String size = ProductCatalog.sizeText(w.size());   // free text
            String combo = comboKey(color, size);
            if (!seen.add(combo)) {
                throw new IllegalArgumentException("اللون والمقاس مكرران: " + label(color, size));
            }

            ProductVariant variant;
            if (w.id() != null) {
                variant = findIn(current, w.id());
                String oldLabel = variant.label();
                String newLabel = label(color, size);
                if (!oldLabel.equals(newLabel)) changes.add("لون/مقاس", oldLabel, newLabel);
            } else {
                // Same colour/size already on the product (maybe inactive): reuse it.
                variant = current.stream()
                        .filter(v -> comboKey(v.getColor(), v.getSize()).equals(combo))
                        .findFirst()
                        .orElse(null);
                if (variant == null) {
                    variant = ProductVariant.builder()
                            .product(product)
                            .code(nextVariantCode(product))
                            .barcode(generateBarcode())
                            .quantity(0)
                            .active(true)
                            .build();
                    current.add(variant);
                }
            }
            boolean isNew = variant.getId() == null;
            boolean reactivated = Boolean.FALSE.equals(variant.getActive());
            variant.setColor(color);
            variant.setSize(size);
            variant.setActive(true);
            if (isNew || reactivated) changes.set("إضافة لون/مقاس", label(color, size));
            variant = variantRepository.save(variant);
            keep.add(variant.getId());
            applyWantedQuantity(variant, w.quantity(), w.expectedQuantity(), isNew, changes);
        }

        if (keep.isEmpty()) {
            throw new IllegalArgumentException("المنتج يجب أن يحتوي على لون/مقاس واحد على الأقل.");
        }

        for (ProductVariant v : new ArrayList<>(current)) {
            if (keep.contains(v.getId()) || Boolean.FALSE.equals(v.getActive())) continue;
            boolean hasHistory = v.getQuantity() > 0 || stockTransactionRepository.existsByVariantId(v.getId());
            if (hasHistory) {
                if (v.getQuantity() > 0) {
                    throw new IllegalArgumentException("لا يمكن حذف " + v.label() + " لأن به مخزون (" + v.getQuantity()
                            + "). بِع الكمية أو اتركه.");
                }
                v.setActive(false);
                variantRepository.save(v);
            } else {
                current.remove(v);
                variantRepository.delete(v);
            }
            changes.add("حذف لون/مقاس", v.label(), null);
        }
    }

    /**
     * Sets a colour/size to the quantity typed in the product form, going
     * through StockService so the ledger records it: opening stock for a
     * new colour/size, an adjustment (in or out) for an existing one.
     */
    private void applyWantedQuantity(ProductVariant variant, Integer wanted, Integer expected, boolean isNew, Changes changes) {
        // Checked even when this row's quantity is unchanged: with a total, the
        // remainder was computed from what the form showed, so stale stock
        // anywhere must be rejected rather than silently absorbed.
        if (!isNew && expected != null && !expected.equals(variant.getQuantity())) {
            throw new ConflictException("تغيّرت كمية " + variant.label() + " منذ فتح النموذج (كانت " + expected
                    + " وأصبحت " + variant.getQuantity() + " بسبب بيع أو شراء). أعد فتح المنتج ثم عدّل الكمية.");
        }
        if (wanted == null) return;
        if (wanted < 0) throw new IllegalArgumentException("الكمية لا يمكن أن تكون سالبة (" + variant.label() + ")");
        if (wanted > ProductFieldRules.MAX_QUANTITY) throw new IllegalArgumentException("الكمية كبيرة جداً (" + variant.label() + ")");
        int diff = wanted - variant.getQuantity();
        if (diff == 0) return;
        changes.add("الكمية (" + variant.label() + ")", variant.getQuantity(), wanted);
        if (diff > 0) {
            stockService.increaseStock(variant.getId(), diff,
                    isNew ? TransactionType.STOCK_IN : TransactionType.ADJUSTMENT_IN, null, variant.getProduct().getId(),
                    REFERENCE_PRODUCT_FORM, isNew ? "رصيد افتتاحي من شاشة المنتج" : "تعديل الكمية من شاشة المنتج");
        } else {
            stockService.decreaseStock(variant.getId(), -diff, TransactionType.ADJUSTMENT_OUT, null, variant.getProduct().getId(),
                    REFERENCE_PRODUCT_FORM, "تعديل الكمية من شاشة المنتج");
        }
    }

    @Override
    public ProductVariantResponse addVariant(Long productId, String color, String size) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        String c = resolveCatalog(ProductCatalog.COLORS, color, "اللون", null);
        String sz = ProductCatalog.sizeText(size);
        String combo = comboKey(c, sz);

        ProductVariant variant = product.getVariants().stream()
                .filter(v -> comboKey(v.getColor(), v.getSize()).equals(combo))
                .findFirst()
                .orElse(null);
        if (variant == null) {
            variant = ProductVariant.builder()
                    .product(product).color(c).size(sz)
                    .code(nextVariantCode(product)).barcode(generateBarcode())
                    .quantity(0).active(true).build();
            product.getVariants().add(variant);
        }
        variant.setActive(true);
        return ProductMapper.toVariantResponse(variantRepository.save(variant));
    }

    @Override
    @Transactional(readOnly = true)
    public VariantLookupResponse lookupVariant(String code) {
        String c = code == null ? "" : code.trim();
        ProductVariant v = variantRepository.findByCode(c)
                .or(() -> variantRepository.findByBarcode(c))
                .filter(x -> !Boolean.FALSE.equals(x.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("لا يوجد منتج بالكود أو الباركود \"" + c + "\""));
        return new VariantLookupResponse(productMapper.toResponse(v.getProduct()), ProductMapper.toVariantResponse(v));
    }

    private static ProductVariant findIn(List<ProductVariant> current, Long id) {
        return current.stream().filter(v -> v.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("اللون/المقاس رقم " + id + " لا يخص هذا المنتج."));
    }

    /**
     * Blank = none. Accepts the stored value or the UI label. A value that is
     * not in the list is rejected, except when it is the variant's current
     * (legacy) value, so old data such as "200x230" can still be saved.
     */
    private static String resolveCatalog(Map<String, String> catalog, String raw, String label, String currentValue) {
        String v = ProductCatalog.normalize(raw);
        if (v.isEmpty()) return null;
        if (currentValue != null && ProductCatalog.normalize(currentValue).equals(v)) return currentValue;
        return ProductCatalog.resolve(catalog, v)
                .orElseThrow(() -> new IllegalArgumentException(label + " \"" + raw + "\" غير موجود في القائمة"));
    }

    private static String productLabel(Product p) {
        return p.getName() + " (" + p.getCode() + ")";
    }

    private static String comboKey(String color, String size) {
        return ProductCatalog.normalize(color) + "|" + ProductCatalog.normalize(size);
    }

    private static String label(String color, String size) {
        return ProductVariant.builder().color(color).size(size).build().label();
    }

    /** PRD-XXXX-1, -2, ...: the next free suffix for this product. */
    private String nextVariantCode(Product product) {
        int n = product.getVariants().size() + 1;
        String code;
        do {
            code = product.getCode() + "-" + n++;
        } while (variantRepository.existsByCode(code));
        return code;
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {

        Product product =
                productRepository.findById(id)

                        .orElseThrow(
                                () -> new ProductNotFoundException(id)
                        );


        return productMapper.toResponse(product);
    }


    // =========================================================
    // GET CURRENT PRICE
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public ProductPriceResponse getPrice(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        ProductPriceResponse price = productMapper.toPriceResponse(product);
        if (price == null) {
            throw new PriceNotFoundException(productId);
        }
        return price;
    }



    // =========================================================
    // DELETE (soft)
    // =========================================================

    @Override
    public void delete(Long id) {

        Product product =
                productRepository.findById(id)

                        .orElseThrow(
                                () -> new ProductNotFoundException(id)
                        );

        // Soft delete: products are referenced by invoices, stock
        // and transfers, so a hard delete would break history.
        product.setActive(false);

        productRepository.save(product);

        activityLog.record(ActivityEntity.PRODUCT, ActivityAction.STATUS, product.getId(), productLabel(product),
                "تعطيل منتج «" + product.getName() + "»");
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {

        return productRepository.findAll()

                .stream()

                .filter(product ->
                        Boolean.TRUE.equals(product.getActive())
                )

                .map(productMapper::toResponse)

                .toList();
    }


    // =========================================================
    // TOGGLE STATUS
    // =========================================================

    @Override
    public void toggleStatus(Long id) {

        Product product =
                productRepository.findById(id)

                        .orElseThrow(
                                () -> new ProductNotFoundException(id)
                        );


        product.setActive(
                !Boolean.TRUE.equals(product.getActive())
        );


        productRepository.save(product);

        activityLog.record(ActivityEntity.PRODUCT, ActivityAction.STATUS, product.getId(), productLabel(product),
                (product.getActive() ? "تفعيل" : "تعطيل") + " منتج «" + product.getName() + "»");
    }


    // =========================================================
    // SEARCH
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> search(
            String keyword) {

        return productRepository.search(keyword)

                .stream()

                .filter(product ->
                        Boolean.TRUE.equals(product.getActive())
                )

                .map(productMapper::toResponse)

                .toList();
    }




    // =========================================================
    // SET PRICE (create or change)
    // =========================================================

    /**
     * Sets the product's current price. Works the same for the first
     * price and for later changes.
     *
     * Rules: purchase price > 0, plus exactly one of profit % (>= 0) or
     * selling price (> 0); the other one is calculated.
     */
    @Override
    public ProductResponse setPrice(
            Long productId,
            CreateProductPriceRequest request) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (request == null || request.purchasePrice() == null) {
            throw new IllegalArgumentException("Purchase price is required.");
        }

        BigDecimal purchasePrice = request.purchasePrice();
        BigDecimal profitPercentage = request.profitPercentage();
        BigDecimal sellingPrice = request.sellingPrice();

        if (purchasePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Purchase price must be greater than zero.");
        }
        if (profitPercentage != null && sellingPrice != null) {
            throw new IllegalArgumentException("Send either sellingPrice or profitPercentage, not both.");
        }
        if (profitPercentage == null && sellingPrice == null) {
            throw new IllegalArgumentException("Send sellingPrice or profitPercentage with the purchase price.");
        }

        // Round the cost first, so the derived value is computed from the
        // same number that gets stored (and matches the frontend preview).
        purchasePrice = purchasePrice.setScale(2, RoundingMode.HALF_UP);

        if (profitPercentage != null) {
            if (profitPercentage.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Profit percentage cannot be negative.");
            }
            sellingPrice = calculateSellingPrice(purchasePrice, profitPercentage);
        } else {
            if (sellingPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Selling price must be greater than zero.");
            }
            profitPercentage = calculateProfitPercentage(purchasePrice, sellingPrice);
        }
        // profit_percentage is NUMERIC(7,2): an absurd ratio (cost 0.01,
        // price 1000) would overflow it with a raw database error.
        if (profitPercentage.compareTo(ProductFieldRules.MAX_PROFIT) > 0) {
            throw new IllegalArgumentException("نسبة الربح أكبر من الحد المسموح (" + ProductFieldRules.MAX_PROFIT
                    + "%). راجع سعر التكلفة وسعر البيع.");
        }

        sellingPrice = sellingPrice.setScale(2, RoundingMode.HALF_UP);

        Changes changes = Changes.of()
                .add("سعر التكلفة", product.getPurchasePrice(), purchasePrice)
                .add("نسبة الربح %", product.getProfitPercentage(), profitPercentage)
                .add("سعر البيع", product.getSellingPrice(), sellingPrice);
        boolean firstPrice = product.getSellingPrice() == null;

        product.setPurchasePrice(purchasePrice);
        product.setSellingPrice(sellingPrice);
        product.setProfitPercentage(profitPercentage);
        product.setPriceUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        if (!changes.isEmpty()) {
            activityLog.record(ActivityEntity.PRODUCT, ActivityAction.PRICE, product.getId(), productLabel(product),
                    (firstPrice ? "تسعير منتج «" : "تغيير سعر منتج «") + product.getName() + "»", changes);
        }

        return productMapper.toResponse(product);
    }


    // =========================================================
    // LOW STOCK
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStock() {

        return productRepository.findLowStock()
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }


    // =========================================================
    // PRIVATE METHODS
    // =========================================================

    private String generateProductCode() {

        String code;

        do {

            code =
                    "PRD-"
                            + UUID.randomUUID()
                            .toString()
                            .substring(0, 8)
                            .toUpperCase();

        } while (
                productRepository.existsByCode(code)
        );


        return code;
    }


    private String generateBarcode() {

        String barcode;

        do {

            long randomNumber =
                    (long) (
                            Math.random()
                                    * 1_000_000_000L
                    );


            barcode =
                    String.format(
                            "200%09d",
                            randomNumber
                    );

        } while (
                variantRepository.existsByBarcode(barcode)
        );


        return barcode;
    }




    private BigDecimal calculateSellingPrice(
            BigDecimal purchase,
            BigDecimal profit) {

        return purchase

                .add(
                        purchase
                                .multiply(profit)
                                .divide(
                                        BigDecimal.valueOf(100),
                                        2,
                                        RoundingMode.HALF_UP
                                )
                )

                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }


    private BigDecimal calculateProfitPercentage(
            BigDecimal purchase,
            BigDecimal selling) {

        if (purchase.compareTo(BigDecimal.ZERO) == 0) {

            throw new IllegalArgumentException(
                    "Purchase price cannot be zero"
            );
        }


        return selling

                .subtract(purchase)

                .multiply(
                        BigDecimal.valueOf(100)
                )

                .divide(
                        purchase,
                        2,
                        RoundingMode.HALF_UP
                );
    }
}