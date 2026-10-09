package Elramy.Group.MafroshartElramyz.dto.product;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Field rules shared by the Excel import and the draft editor, so both
 * agree on what is valid and what makes a product complete.
 *
 * Complete = name, type, colour, size, price (cost + profit % or selling
 * price) and quantity (0 counts). Model and minimum quantity are optional.
 */
public final class ProductFieldRules {

    private ProductFieldRules() {
    }

    public static final int MAX_NAME = 200;
    public static final int MAX_MODEL = 100;
    public static final int MAX_QUANTITY = 1_000_000;
    public static final BigDecimal MAX_PRICE = new BigDecimal("10000000");
    public static final BigDecimal MAX_PROFIT = new BigDecimal("1000");

    // Arabic labels, used in missing-field lists and notes.
    public static final String NAME = "الاسم";
    public static final String TYPE = "النوع";
    public static final String COLOR = "اللون";
    public static final String SIZE = "المقاس";
    public static final String PRICE = "السعر";
    public static final String QUANTITY = "الكمية الحالية";

    /**
     * Required fields that are still empty. Empty list = complete.
     * Colour and size are optional (V11): a variant may have neither.
     */
    public static List<String> missingFields(String name, String type,
                                             BigDecimal purchasePrice, BigDecimal profitPercentage,
                                             BigDecimal sellingPrice, Integer quantity) {
        List<String> missing = new ArrayList<>();
        if (name == null || name.isBlank()) missing.add(NAME);
        if (type == null || type.isBlank()) missing.add(TYPE);
        if (!hasPrice(purchasePrice, profitPercentage, sellingPrice)) missing.add(PRICE);
        if (quantity == null) missing.add(QUANTITY);
        return missing;
    }

    public static boolean hasPrice(BigDecimal purchasePrice, BigDecimal profitPercentage, BigDecimal sellingPrice) {
        return purchasePrice != null && purchasePrice.signum() > 0
                && ((sellingPrice != null && sellingPrice.signum() > 0) || profitPercentage != null);
    }

    /**
     * The price request for a complete product: the explicit selling price
     * wins when both are present (the form keeps them consistent).
     */
    public static CreateProductPriceRequest priceRequest(BigDecimal purchasePrice, BigDecimal profitPercentage,
                                                         BigDecimal sellingPrice) {
        BigDecimal cost = purchasePrice.setScale(2, RoundingMode.HALF_UP);
        return sellingPrice != null && sellingPrice.signum() > 0
                ? new CreateProductPriceRequest(cost, null, sellingPrice.setScale(2, RoundingMode.HALF_UP))
                : new CreateProductPriceRequest(cost, profitPercentage.setScale(2, RoundingMode.HALF_UP), null);
    }

    /** Product identity: same name, type and model = same product. */
    public static String productKey(String name, String type, String model) {
        return String.join("|",
                ProductCatalog.normalize(name).toLowerCase(Locale.ROOT),
                type == null ? "" : type.trim(),
                ProductCatalog.normalize(model).toLowerCase(Locale.ROOT));
    }

    /** Variant identity within a product: colour + size (either may be empty). */
    public static final String TOTAL = "إجمالي الكمية";

    /**
     * Applies the "total available quantity" of the product form: the
     * colours/sizes may add up to at most {@code total}, and what is left is
     * kept on the "no colour or size" row (added when missing), so the stock
     * always equals the total typed. A row without a quantity counts with
     * {@code currentOf} (its saved stock; 0 for a new row).
     */
    public static List<ProductVariantRequest> applyTotal(List<ProductVariantRequest> wanted, Integer total,
                                                         Function<Long, Integer> currentOf) {
        if (total == null) return wanted;
        if (total < 0 || total > MAX_QUANTITY) {
            throw new IllegalArgumentException(TOTAL + " يجب أن يكون بين 0 و " + MAX_QUANTITY);
        }
        List<ProductVariantRequest> rows = new ArrayList<>(wanted == null ? List.of() : wanted);
        int sum = 0;
        for (ProductVariantRequest w : rows) sum += quantityOf(w, currentOf);
        if (sum > total) {
            throw new IllegalArgumentException("مجموع كميات الألوان/المقاسات (" + sum + ") أكبر من " + TOTAL + " (" + total + ").");
        }
        int rest = total - sum;
        if (rest == 0) return rows;
        for (int i = 0; i < rows.size(); i++) {
            ProductVariantRequest w = rows.get(i);
            if (comboKey(w.color(), w.size()).equals("|")) {
                rows.set(i, new ProductVariantRequest(w.id(), w.color(), w.size(), quantityOf(w, currentOf) + rest, w.expectedQuantity()));
                return rows;
            }
        }
        rows.add(new ProductVariantRequest(null, null, null, rest, null));
        return rows;
    }

    private static int quantityOf(ProductVariantRequest w, Function<Long, Integer> currentOf) {
        if (w.quantity() != null) return w.quantity();
        if (w.id() == null) return 0;
        Integer current = currentOf.apply(w.id());
        return current == null ? 0 : current;
    }

    public static String comboKey(String color, String size) {
        return ProductCatalog.normalize(color) + "|" + ProductCatalog.normalize(size);
    }

    /** True when name and type are known, so the product key is meaningful. */
    public static boolean identifiable(String name, String type) {
        return name != null && !name.isBlank() && type != null;
    }
}
