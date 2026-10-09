package Elramy.Group.MafroshartElramyz.stocktake;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Requests and responses of /api/stocktakes. */
public final class StocktakeDtos {

    private StocktakeDtos() {
    }

    public static final int MAX_COUNT = 99_999;

    /** Empty or null {@code types} = the whole store. */
    public record CreateRequest(
            @NotBlank(message = "عنوان الجرد مطلوب") @Size(max = 200, message = "العنوان طويل جداً") String title,
            List<String> types,
            @Size(max = 500, message = "الملاحظات طويلة جداً") String notes) {
    }

    public record CountRequest(
            @NotNull(message = "الكمية مطلوبة")
            @Min(value = 0, message = "الكمية لا يمكن أن تكون سالبة")
            @Max(value = MAX_COUNT, message = "الكمية كبيرة جداً") Integer quantity) {
    }

    public record ScanRequest(@NotBlank(message = "الكود مطلوب") @Size(max = 100) String code) {
    }

    public record ReasonRequest(StocktakeReason reason, @Size(max = 500, message = "الملاحظة طويلة جداً") String note) {
    }

    public record Summary(
            Long id, String code, String title, List<String> types, StocktakeStatus status, String notes,
            LocalDateTime createdAt, String createdByName, LocalDateTime approvedAt, String approvedByName,
            int countedLines, int totalInScope,
            BigDecimal shortageCost, BigDecimal surplusCost, BigDecimal shortageSell, BigDecimal surplusSell) {
    }

    /** A colour/size as the counter sees it: no system quantity (blind count). */
    public record CountLine(
            Long variantId, Long productId, String productName, String itemType, String variantCode, String barcode,
            String color, String size, String label, int countedQty, LocalDateTime countedAt) {
    }

    public record CountView(Summary stocktake, List<CountLine> lines) {
    }

    public record ReviewLine(
            Long variantId, Long productId, String productName, String itemType, String variantCode,
            String label, int systemQty, int countedQty, int variance,
            BigDecimal unitCost, BigDecimal unitPrice, BigDecimal costValue, BigDecimal sellValue,
            StocktakeReason reason, String note, Integer appliedVariance, boolean clamped) {
    }

    public record Uncounted(Long variantId, String productName, String variantCode, String label) {
    }

    public record Review(
            Summary stocktake, List<ReviewLine> lines, List<Uncounted> uncounted,
            int linesWithDifference, BigDecimal shortageCost, BigDecimal surplusCost,
            BigDecimal shortageSell, BigDecimal surplusSell) {
    }

    /** Result of uploading a filled count sheet. */
    public record ImportResult(int filled, List<String> unknown, List<String> outOfScope, List<String> invalid) {
    }

    /** One colour/size the scanner or search found, for the counting screen. */
    public record Scanned(CountLine line, boolean created) {
    }
}
