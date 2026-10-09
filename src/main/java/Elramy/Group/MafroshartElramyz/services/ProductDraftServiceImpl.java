package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.enums.ProductType;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.dto.product.CreateProductPriceRequest;
import Elramy.Group.MafroshartElramyz.dto.product.CreateProductRequest;
import Elramy.Group.MafroshartElramyz.dto.product.ProductCatalog;
import Elramy.Group.MafroshartElramyz.dto.product.ProductDraftRequest;
import Elramy.Group.MafroshartElramyz.dto.product.ProductDraftResponse;
import Elramy.Group.MafroshartElramyz.dto.product.ProductDraftVariantDto;
import Elramy.Group.MafroshartElramyz.dto.product.ProductFieldRules;
import Elramy.Group.MafroshartElramyz.dto.product.ProductResponse;
import Elramy.Group.MafroshartElramyz.dto.product.ProductVariantRequest;
import Elramy.Group.MafroshartElramyz.dto.product.ProductVariantResponse;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductDraft;
import Elramy.Group.MafroshartElramyz.models.ProductDraftVariant;
import Elramy.Group.MafroshartElramyz.repository.ProductDraftRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductDraftServiceImpl implements ProductDraftService {

    /** Ledger reference for opening stock entered when a draft is completed. */
    static final String REFERENCE_PRODUCT_DRAFT = "PRODUCT_DRAFT";

    private final ProductDraftRepository draftRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final StockService stockService;
    private final ActivityLogService activityLog;


    @Override
    @Transactional(readOnly = true)
    public List<ProductDraftResponse> list() {
        return draftRepository.findAllByOrderByCreatedAtDescIdDesc().stream().map(ProductDraftServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return draftRepository.count();
    }

    @Override
    public ProductDraftResponse update(Long id, ProductDraftRequest r) {
        ProductDraft d = find(id);

        String name = blankToNull(r.name());
        if (name != null && name.length() > ProductFieldRules.MAX_NAME) {
            throw new IllegalArgumentException("الاسم يجب ألا يزيد عن " + ProductFieldRules.MAX_NAME + " حرف");
        }
        String model = blankToNull(r.model());
        if (model != null && model.length() > ProductFieldRules.MAX_MODEL) {
            throw new IllegalArgumentException("الموديل يجب ألا يزيد عن " + ProductFieldRules.MAX_MODEL + " حرف");
        }
        ProductType type = blankToNull(r.itemType()) == null ? null : ProductCatalog.resolveType(r.itemType())
                .orElseThrow(() -> new IllegalArgumentException("النوع \"" + r.itemType() + "\" غير موجود في القائمة"));
        Integer minimum = wholeNumber(r.minimumQuantity(), "الحد الأدنى");
        Integer total = wholeNumber(r.totalQuantity(), ProductFieldRules.TOTAL);
        BigDecimal purchase = positive(r.purchasePrice(), "سعر التكلفة");
        BigDecimal selling = positive(r.sellingPrice(), "سعر البيع");
        if (r.profitPercentage() != null && r.profitPercentage().compareTo(ProductFieldRules.MAX_PROFIT) > 0) {
            throw new IllegalArgumentException("نسبة الربح كبيرة جداً");
        }

        // Validate every colour/size row before changing anything.
        List<ProductDraftVariantDto> wanted = null;
        if (r.variants() != null) {
            wanted = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ProductDraftVariantDto v : r.variants()) {
                String color = blankToNull(v.color()) == null ? null : ProductCatalog.resolve(ProductCatalog.COLORS, v.color())
                        .orElseThrow(() -> new IllegalArgumentException("اللون \"" + v.color() + "\" غير موجود في القائمة"));
                String size = ProductCatalog.sizeText(v.size());   // free text
                if (!seen.add(ProductFieldRules.comboKey(color, size))) {
                    throw new IllegalArgumentException("اللون/المقاس مكرر: " + label(color, size));
                }
                wanted.add(new ProductDraftVariantDto(v.id(), color, size, wholeNumber(v.quantity(), "الكمية"), v.sourceRow()));
            }
            if (wanted.isEmpty()) {
                throw new IllegalArgumentException("أضف لوناً/مقاساً واحداً على الأقل (أو صفاً بدون لون ومقاس).");
            }
        }

        Changes changes = Changes.of()
                .add("الاسم", d.getName(), name)
                .add("النوع", d.getItemType(), type)
                .add("الموديل", d.getModel(), model)
                .add("الحد الأدنى", d.getMinimumQuantity(), minimum)
                .add(ProductFieldRules.TOTAL, d.getTotalQuantity(), total)
                .add("سعر التكلفة", d.getPurchasePrice(), purchase)
                .add("نسبة الربح %", d.getProfitPercentage(), r.profitPercentage())
                .add("سعر البيع", d.getSellingPrice(), selling);
        String variantsBefore = variantsSummary(d);

        d.setName(name);
        d.setModel(model);
        d.setItemType(type);
        d.setMinimumQuantity(minimum);
        d.setPurchasePrice(purchase);
        d.setSellingPrice(selling);
        // Profit may be negative: it is derived when selling below cost.
        d.setProfitPercentage(r.profitPercentage());

        if (wanted != null) {
            Map<Long, ProductDraftVariant> byId = d.getVariants().stream()
                    .filter(v -> v.getId() != null)
                    .collect(Collectors.toMap(ProductDraftVariant::getId, v -> v));
            List<ProductDraftVariant> next = new ArrayList<>();
            for (ProductDraftVariantDto w : wanted) {
                ProductDraftVariant v = w.id() != null ? byId.get(w.id()) : null;
                if (v == null) {
                    v = ProductDraftVariant.builder().draft(d).sourceRow(w.sourceRow()).build();
                }
                v.setColor(w.color());
                v.setSize(w.size());
                v.setQuantity(w.quantity());
                next.add(v);
            }
            d.getVariants().clear();          // orphanRemoval deletes the dropped rows
            d.getVariants().addAll(next);

            // The person saw (and decided) colours and sizes, including "none".
            clearVariantNotes(d);
        }

        // Same rule as the product form: colours/sizes may not exceed the total.
        int distributed = distributed(d);
        if (total != null && distributed > total) {
            throw new IllegalArgumentException("مجموع كميات الألوان/المقاسات (" + distributed + ") أكبر من "
                    + ProductFieldRules.TOTAL + " (" + total + ").");
        }
        d.setTotalQuantity(total);

        ProductDraft saved = draftRepository.save(d);
        changes.add("الألوان والمقاسات", variantsBefore, variantsSummary(saved));
        if (!changes.isEmpty()) {
            activityLog.record(ActivityEntity.PRODUCT_DRAFT, ActivityAction.UPDATE, saved.getId(), draftLabel(saved),
                    "تعديل منتج غير مكتمل «" + draftLabel(saved) + "»", changes);
        }
        return toResponse(saved);
    }

    /**
     * Creates the product with all of the draft's colours/sizes, or - when a
     * product with the same name, type and model already exists - adds them
     * to it. Sets the price (if the product has none) and records each
     * colour/size's opening stock. One transaction; the draft is deleted.
     */
    @Override
    public ProductResponse complete(Long id) {
        ProductDraft d = find(id);

        List<String> missing = missing(d);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("لا يمكن إضافة المنتج قبل إكمال: " + String.join("، ", missing));
        }

        // What the colours/sizes leave of the total becomes "no colour or size",
        // exactly like the product form (ProductFieldRules.applyTotal).
        int rest = d.getTotalQuantity() - distributed(d);
        if (rest > 0) {
            ProductDraftVariant plain = d.getVariants().stream()
                    .filter(v -> ProductFieldRules.comboKey(v.getColor(), v.getSize()).equals("|"))
                    .findFirst().orElse(null);
            if (plain != null) plain.setQuantity(plain.getQuantity() + rest);
            else d.addVariant(null, null, rest, null);
        }

        String key = ProductFieldRules.productKey(d.getName(), d.getItemType(), d.getModel());
        CreateProductPriceRequest price = ProductFieldRules.priceRequest(d.getPurchasePrice(), d.getProfitPercentage(), d.getSellingPrice());

        Product existing = productRepository.findAll().stream()
                .filter(p -> ProductFieldRules.productKey(p.getName(), p.getItemType(), p.getModel()).equals(key))
                .min((a, b) -> Boolean.compare(Boolean.FALSE.equals(a.getActive()), Boolean.FALSE.equals(b.getActive())))
                .orElse(null);

        Long productId;
        List<ProductVariantResponse> targets = new ArrayList<>();
        if (existing != null) {
            if (Boolean.FALSE.equals(existing.getActive())) {
                throw new IllegalArgumentException("يوجد منتج مطابق غير نشط بالكود " + existing.getCode() + ". فعّله أولاً أو غيّر بيانات هذه المسودة.");
            }
            Set<String> existingCombos = existing.activeVariants().stream()
                    .map(v -> ProductFieldRules.comboKey(v.getColor(), v.getSize()))
                    .collect(Collectors.toSet());
            List<String> clashes = d.getVariants().stream()
                    .filter(v -> existingCombos.contains(ProductFieldRules.comboKey(v.getColor(), v.getSize())))
                    .map(v -> label(v.getColor(), v.getSize()))
                    .toList();
            if (!clashes.isEmpty()) {
                throw new IllegalArgumentException("المنتج " + existing.getCode() + " يحتوي بالفعل على: " + String.join("، ", clashes)
                        + ". لزيادة الكمية استخدم فاتورة شراء، واحذف هذه الألوان/المقاسات من المسودة.");
            }
            productId = existing.getId();
            for (ProductDraftVariant v : d.getVariants()) {
                targets.add(productService.addVariant(productId, v.getColor(), v.getSize()));
            }
            if (existing.getSellingPrice() == null) {
                productService.setPrice(productId, price);
            }
        } else {
            ProductResponse created = productService.create(new CreateProductRequest(
                    d.getName().trim(), d.getModel(), d.getItemType(),
                    d.getMinimumQuantity() != null ? d.getMinimumQuantity() : 0,
                    d.getVariants().stream().map(v -> new ProductVariantRequest(null, v.getColor(), v.getSize())).toList()));
            productId = created.id();
            // Variants come back in the order they were requested.
            targets.addAll(created.variants());
            productService.setPrice(productId, price);
        }

        for (int i = 0; i < d.getVariants().size(); i++) {
            int qty = d.getVariants().get(i).getQuantity();
            if (qty > 0) {
                stockService.increaseStock(targets.get(i).id(), qty, TransactionType.STOCK_IN, price.purchasePrice(),
                        d.getId(), REFERENCE_PRODUCT_DRAFT, "رصيد افتتاحي عند إكمال منتج غير مكتمل");
            }
        }

        activityLog.record(ActivityEntity.PRODUCT_DRAFT, ActivityAction.COMPLETE, d.getId(), draftLabel(d),
                "إضافة منتج غير مكتمل «" + draftLabel(d) + "» إلى المنتجات" + (existing != null ? " (أُضيفت الألوان/المقاسات لمنتج موجود)" : ""),
                Changes.of().set("الألوان والمقاسات", variantsSummary(d)));
        draftRepository.delete(d);
        return productService.getById(productId);
    }

    @Override
    public void delete(Long id) {
        ProductDraft d = find(id);
        draftRepository.delete(d);
        activityLog.record(ActivityEntity.PRODUCT_DRAFT, ActivityAction.DELETE, id, draftLabel(d),
                "حذف منتج غير مكتمل «" + draftLabel(d) + "»",
                Changes.of().add("الألوان والمقاسات", variantsSummary(d), null));
    }


    // ---------------------------------------------------------------- helpers

    private ProductDraft find(Long id) {
        return draftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("المنتج غير المكتمل رقم " + id + " غير موجود"));
    }

    /**
     * Required fields still empty: name, type, price, and a quantity on
     * every colour/size row. Colour and size are optional, except when the
     * import had to clear an invalid value: then the draft counts as missing
     * it until someone saves the form (see {@link #update}).
     */
    static List<String> missing(ProductDraft d) {
        boolean quantitiesComplete = !d.getVariants().isEmpty()
                && d.getVariants().stream().allMatch(v -> v.getQuantity() != null);
        List<String> m = new ArrayList<>(ProductFieldRules.missingFields(d.getName(), d.getItemType(),
                d.getPurchasePrice(), d.getProfitPercentage(), d.getSellingPrice(), quantitiesComplete ? 0 : null));
        if (d.getTotalQuantity() == null || distributed(d) > d.getTotalQuantity()) {
            m.add(ProductFieldRules.TOTAL);
        }
        if (hasNoteFor(d, ProductFieldRules.COLOR) && d.getVariants().stream().anyMatch(v -> v.getColor() == null)) {
            m.add(ProductFieldRules.COLOR);
        }
        if (hasNoteFor(d, ProductFieldRules.SIZE) && d.getVariants().stream().anyMatch(v -> v.getSize() == null)) {
            m.add(ProductFieldRules.SIZE);
        }
        return m;
    }

    /** Sum of the quantities entered on the colours/sizes (empty = 0). */
    private static int distributed(ProductDraft d) {
        return d.getVariants().stream().mapToInt(v -> v.getQuantity() == null ? 0 : v.getQuantity()).sum();
    }

    private static boolean hasNoteFor(ProductDraft d, String fieldLabel) {
        return d.getImportNotes() != null
                && Arrays.stream(d.getImportNotes().split("\n")).anyMatch(n -> n.startsWith(fieldLabel + ":"));
    }

    /** Drops the import notes about colour/size: the person has now decided them. */
    private static void clearVariantNotes(ProductDraft d) {
        if (d.getImportNotes() == null) return;
        String kept = Arrays.stream(d.getImportNotes().split("\n"))
                .filter(n -> !n.startsWith(ProductFieldRules.COLOR + ":") && !n.startsWith(ProductFieldRules.SIZE + ":"))
                .collect(Collectors.joining("\n"));
        d.setImportNotes(kept.isBlank() ? null : kept);
    }

    static ProductDraftResponse toResponse(ProductDraft d) {
        ProductType t = d.getItemType();
        List<String> notes = d.getImportNotes() == null || d.getImportNotes().isBlank()
                ? List.of()
                : Arrays.stream(d.getImportNotes().split("\n")).filter(s -> !s.isBlank()).toList();
        List<ProductDraftVariantDto> variants = d.getVariants().stream()
                .map(v -> new ProductDraftVariantDto(v.getId(), v.getColor(), v.getSize(), v.getQuantity(), v.getSourceRow()))
                .toList();
        return new ProductDraftResponse(
                d.getId(), d.getName(), t == null ? null : t.name(), d.getModel(), d.getMinimumQuantity(),
                d.getPurchasePrice(), d.getProfitPercentage(), d.getSellingPrice(),
                variants, missing(d), notes, d.getSourceFile(), d.getSourceRow(), d.getCreatedAt(), d.getTotalQuantity());
    }

    private static String draftLabel(ProductDraft d) {
        return d.getName() != null ? d.getName() : "بدون اسم" + (d.getSourceRow() != null ? " (الصف " + d.getSourceRow() + ")" : "");
    }

    /** "أحمر / صغير: 4، كبير: 3" - for the activity log. */
    private static String variantsSummary(ProductDraft d) {
        return d.getVariants().stream()
                .map(v -> label(v.getColor(), v.getSize()) + ": " + (v.getQuantity() != null ? v.getQuantity() : "؟"))
                .collect(Collectors.joining("، "));
    }

    private static String label(String color, String size) {
        String c = color == null ? "" : color.trim();
        String s = size == null ? "" : size.trim();
        if (!c.isEmpty() && !s.isEmpty()) return c + " / " + s;
        if (!c.isEmpty()) return c;
        if (!s.isEmpty()) return s;
        return "بدون لون أو مقاس";
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String v = ProductCatalog.normalize(s);
        return v.isEmpty() ? null : v;
    }

    private static Integer wholeNumber(Integer v, String label) {
        if (v == null) return null;
        if (v < 0) throw new IllegalArgumentException(label + " لا يمكن أن تكون سالبة");
        if (v > ProductFieldRules.MAX_QUANTITY) throw new IllegalArgumentException(label + " كبيرة جداً");
        return v;
    }

    private static BigDecimal positive(BigDecimal v, String label) {
        if (v == null) return null;
        if (v.signum() <= 0) throw new IllegalArgumentException(label + " يجب أن يكون أكبر من صفر");
        if (v.compareTo(ProductFieldRules.MAX_PRICE) > 0) throw new IllegalArgumentException(label + " كبير جداً");
        return v;
    }
}
