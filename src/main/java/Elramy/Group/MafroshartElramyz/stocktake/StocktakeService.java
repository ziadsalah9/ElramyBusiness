package Elramy.Group.MafroshartElramyz.stocktake;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.exception.ConflictException;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import Elramy.Group.MafroshartElramyz.services.StockService;
import Elramy.Group.MafroshartElramyz.services.UserService;
import Elramy.Group.MafroshartElramyz.stocktake.StocktakeDtos.*;
import static Elramy.Group.MafroshartElramyz.stocktake.StocktakeDtos.MAX_COUNT;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stocktaking (الجرد), ADMIN-only.
 *
 * Counting is blind: the counting view never shows the system quantity.
 * Each counted line keeps the stock at the moment it was counted, and
 * approval applies counted - that snapshot to the CURRENT stock, so
 * sales and purchases made while counting are not undone.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StocktakeService {

    static final String REFERENCE_TYPE = "STOCKTAKE";
    static final int MAX_SHEET_ROWS = 5000;
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    // Count sheet columns (the upload reads them back by header).
    static final String H_CODE = "كود اللون/المقاس";
    static final String H_BARCODE = "الباركود";
    static final String H_PRODUCT = "المنتج";
    static final String H_TYPE = "النوع";
    static final String H_COLOR = "اللون";
    static final String H_SIZE = "المقاس";
    static final String H_COUNT = "الكمية المعدودة";
    private static final String[] HEADERS = {H_CODE, H_BARCODE, H_PRODUCT, H_TYPE, H_COLOR, H_SIZE, H_COUNT};

    private final StocktakeRepository stocktakes;
    private final ProductVariantRepository variants;
    private final StockService stockService;
    private final UserService userService;
    private final ActivityLogService activityLog;
    private final EntityManager entityManager;

    // ------------------------------------------------------------------ create / read

    public Summary create(CreateRequest request) {
        if (stocktakes.findFirstByStatus(StocktakeStatus.OPEN).isPresent()) {
            throw new ConflictException("يوجد جرد مفتوح بالفعل. اعتمده أو ألغِه قبل بدء جرد جديد.");
        }
        String prefix = "STK-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        Stocktake s = Stocktake.builder()
                .code(prefix + (stocktakes.countByCodeStartingWith(prefix) + 1))
                .title(request.title().trim())
                .status(StocktakeStatus.OPEN)
                .notes(blankToNull(request.notes()))
                .createdBy(userService.getCurrentUser())
                .build();
        s.setScope(request.types());
        Stocktake saved = stocktakes.save(s);

        activityLog.record(ActivityEntity.STOCKTAKE, ActivityAction.CREATE, saved.getId(), label(saved),
                "بدء جرد «" + saved.getTitle() + "»",
                Changes.of().set("النطاق", scopeLabel(saved)).set("ملاحظات", saved.getNotes()));
        return summary(saved, countable(saved).size());
    }

    @Transactional(readOnly = true)
    public List<Summary> list() {
        return stocktakes.findAllByOrderByCreatedAtDesc().stream()
                .map(s -> summary(s, s.isOpen() ? countable(s).size() : s.getLines().size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CountView getForCounting(Long id) {
        Stocktake s = find(id);
        List<CountLine> lines = s.getLines().stream()
                .sorted(Comparator.comparing(StocktakeLine::getCountedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(StocktakeService::countLine)
                .toList();
        return new CountView(summary(s, s.isOpen() ? countable(s).size() : s.getLines().size()), lines);
    }

    @Transactional(readOnly = true)
    public Review getReview(Long id) {
        Stocktake s = find(id);
        List<ReviewLine> lines = s.getLines().stream()
                .sorted(Comparator.comparing((StocktakeLine l) -> l.getProduct().getName()).thenComparing(l -> l.getVariant().getId()))
                .map(l -> reviewLine(l, s.isOpen()))
                .toList();

        List<Uncounted> uncounted = List.of();
        int total = s.getLines().size();
        if (s.isOpen()) {
            Set<Long> counted = s.getLines().stream().map(l -> l.getVariant().getId()).collect(Collectors.toSet());
            List<ProductVariant> scope = countable(s);
            total = scope.size();
            uncounted = scope.stream().filter(v -> !counted.contains(v.getId()))
                    .map(v -> new Uncounted(v.getId(), v.getProduct().getName(), v.getCode(), v.label()))
                    .toList();
        }

        Totals t = totals(lines);
        return new Review(summary(s, total), lines, uncounted,
                (int) lines.stream().filter(l -> l.variance() != 0).count(),
                t.shortageCost, t.surplusCost, t.shortageSell, t.surplusSell);
    }

    // ------------------------------------------------------------------ counting

    /** Sets the counted quantity of a colour/size and refreshes its snapshot. */
    public CountLine setCount(Long id, Long variantId, int quantity) {
        Stocktake s = findOpen(id);
        ProductVariant v = variants.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("اللون/المقاس رقم " + variantId + " غير موجود."));
        return countLine(upsert(s, v, quantity));
    }

    /** One scan = one more piece of that colour/size. */
    public Scanned scan(Long id, String code) {
        Stocktake s = findOpen(id);
        String c = code == null ? "" : code.trim();
        ProductVariant v = variants.findByScanCode(c)
                .orElseThrow(() -> new ResourceNotFoundException("لا يوجد منتج بالكود أو الباركود \"" + c + "\""));
        StocktakeLine existing = lineFor(s, v.getId());
        int next = existing == null ? 1 : existing.getCountedQty() + 1;
        if (next > MAX_COUNT) throw new IllegalArgumentException("الكمية كبيرة جداً.");
        return new Scanned(countLine(upsert(s, v, next)), existing == null);
    }

    public void removeLine(Long id, Long variantId) {
        Stocktake s = findOpen(id);
        StocktakeLine line = lineFor(s, variantId);
        if (line != null) s.getLines().remove(line);
    }

    public ReviewLine setReason(Long id, Long variantId, ReasonRequest request) {
        Stocktake s = findOpen(id);
        StocktakeLine line = lineFor(s, variantId);
        if (line == null) throw new ResourceNotFoundException("هذا اللون/المقاس لم يُعد بعد.");
        line.setReason(request.reason());
        line.setNote(blankToNull(request.note()));
        return reviewLine(line, true);
    }

    private StocktakeLine upsert(Stocktake s, ProductVariant v, int quantity) {
        if (quantity < 0 || quantity > MAX_COUNT) throw new IllegalArgumentException("الكمية يجب أن تكون بين 0 و " + MAX_COUNT + ".");
        ensureInScope(s, v);
        StocktakeLine line = lineFor(s, v.getId());
        if (line == null) {
            line = StocktakeLine.builder().stocktake(s).variant(v).product(v.getProduct()).build();
            s.getLines().add(line);
        }
        line.setCountedQty(quantity);
        // The stock right now: the difference is measured against what the
        // system held when the shelf was counted, not at approval time.
        line.setSystemQtyAtCount(v.getQuantity());
        line.setCountedAt(LocalDateTime.now());
        line.setCountedBy(userService.getCurrentUser());
        stocktakes.save(s);
        return line;
    }

    private void ensureInScope(Stocktake s, ProductVariant v) {
        Product p = v.getProduct();
        if (!Boolean.TRUE.equals(v.getActive()) || !Boolean.TRUE.equals(p.getActive())) {
            throw new IllegalArgumentException(p.getName() + " (" + v.label() + ") غير نشط ولا يدخل في الجرد.");
        }
        List<String> scope = s.scope();
        if (!scope.isEmpty() && !scope.contains(p.getItemType())) {
            throw new IllegalArgumentException(p.getName() + " من نوع «" + p.getItemType() + "» وهو خارج نطاق هذا الجرد.");
        }
    }

    // ------------------------------------------------------------------ approve / cancel

    /**
     * Applies every non-zero difference to the current stock through the
     * ledger (ADJUSTMENT_IN/OUT, reference STOCKTAKE). Rows are locked in
     * variant-id order, like invoices, so this cannot deadlock with a sale.
     */
    public Review approve(Long id) {
        Stocktake s = findOpen(id);
        if (s.getLines().isEmpty()) {
            throw new IllegalArgumentException("لم يتم عد أي منتج في هذا الجرد.");
        }
        String note = "جرد " + s.getCode();
        Changes changes = Changes.of();

        List<StocktakeLine> ordered = s.getLines().stream()
                .sorted(Comparator.comparing(l -> l.getVariant().getId())).toList();
        for (StocktakeLine line : ordered) {
            Product p = line.getProduct();
            line.setUnitCost(p.getPurchasePrice());
            line.setUnitPrice(p.getSellingPrice());
            int variance = line.variance();
            int applied = variance;
            if (variance > 0) {
                stockService.increaseStock(line.getVariant().getId(), variance, TransactionType.ADJUSTMENT_IN,
                        p.getPurchasePrice(), s.getId(), REFERENCE_TYPE, note);
            } else if (variance < 0) {
                // Re-read under lock: sales after counting may have left less
                // than the shortage. Stock never goes below 0.
                ProductVariant v = entityManager.find(ProductVariant.class, line.getVariant().getId());
                entityManager.refresh(v, LockModeType.PESSIMISTIC_WRITE);
                int take = Math.min(-variance, v.getQuantity());
                applied = -take;
                line.setClamped(take < -variance);
                if (take > 0) {
                    stockService.decreaseStock(v.getId(), take, TransactionType.ADJUSTMENT_OUT,
                            p.getPurchasePrice(), s.getId(), REFERENCE_TYPE, note);
                }
            }
            line.setAppliedVariance(applied);
            if (variance != 0) {
                changes.add(p.getName() + " (" + line.getVariant().label() + ")",
                        line.getSystemQtyAtCount(), line.getCountedQty()
                                + (line.getClamped() ? " (طُبق " + applied + " فقط)" : ""));
            }
        }

        Totals t = totals(s.getLines().stream().map(l -> reviewLine(l, false)).toList());
        s.setShortageCost(t.shortageCost);
        s.setSurplusCost(t.surplusCost);
        s.setShortageSell(t.shortageSell);
        s.setSurplusSell(t.surplusSell);
        s.setStatus(StocktakeStatus.APPROVED);
        s.setApprovedBy(userService.getCurrentUser());
        s.setApprovedAt(LocalDateTime.now());
        stocktakes.save(s);

        long withDiff = s.getLines().stream().filter(l -> l.variance() != 0).count();
        activityLog.record(ActivityEntity.STOCKTAKE, ActivityAction.COMPLETE, s.getId(), label(s),
                "اعتماد جرد «" + s.getTitle() + "»: " + s.getLines().size() + " صنف معدود، " + withDiff + " بفروق"
                        + " (عجز " + plain(t.shortageCost) + " / زيادة " + plain(t.surplusCost) + " ج.م بسعر التكلفة)",
                changes);
        return getReview(s.getId());
    }

    public Summary cancel(Long id) {
        Stocktake s = findOpen(id);
        s.setStatus(StocktakeStatus.CANCELLED);
        stocktakes.save(s);
        activityLog.record(ActivityEntity.STOCKTAKE, ActivityAction.DELETE, s.getId(), label(s),
                "إلغاء جرد «" + s.getTitle() + "» بدون تغيير المخزون",
                Changes.of().set("أصناف معدودة", s.getLines().size()));
        return summary(s, s.getLines().size());
    }

    // ------------------------------------------------------------------ Excel count sheet

    /** Every colour/size in scope, with the counts already entered (no system quantity). */
    @Transactional(readOnly = true)
    public byte[] buildSheet(Long id) {
        Stocktake s = find(id);
        Map<Long, Integer> counted = s.getLines().stream()
                .collect(Collectors.toMap(l -> l.getVariant().getId(), StocktakeLine::getCountedQty));
        List<ProductVariant> rows = s.isOpen() ? countable(s) : s.getLines().stream().map(StocktakeLine::getVariant).toList();

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("الجرد");
            sheet.setRightToLeft(true);

            CellStyle header = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle editable = wb.createCellStyle();
            editable.setLocked(false);
            editable.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            editable.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row h = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(header);
            }
            int r = 1;
            for (ProductVariant v : rows) {
                Row row = sheet.createRow(r++);
                // Text cells only: values come from the database, so a name
                // starting with "=" can never become a formula.
                row.createCell(0).setCellValue(v.getCode());
                row.createCell(1).setCellValue(Objects.toString(v.getBarcode(), ""));
                row.createCell(2).setCellValue(v.getProduct().getName());
                row.createCell(3).setCellValue(Objects.toString(v.getProduct().getItemType(), ""));
                row.createCell(4).setCellValue(Objects.toString(v.getColor(), ""));
                row.createCell(5).setCellValue(Objects.toString(v.getSize(), ""));
                Cell count = row.createCell(6);
                count.setCellStyle(editable);
                Integer q = counted.get(v.getId());
                if (q != null) count.setCellValue(q);
            }
            if (r > 1) {
                DataValidationHelper dv = sheet.getDataValidationHelper();
                DataValidation rule = dv.createValidation(
                        dv.createIntegerConstraint(DataValidationConstraint.OperatorType.BETWEEN, "0", String.valueOf(MAX_COUNT)),
                        new CellRangeAddressList(1, r - 1, 6, 6));
                rule.setShowErrorBox(true);
                rule.createErrorBox("قيمة غير صحيحة", "اكتب عدداً صحيحاً من 0 إلى " + MAX_COUNT);
                sheet.addValidationData(rule);
            }
            for (int c = 0; c < HEADERS.length; c++) sheet.setColumnWidth(c, c == 2 ? 30 * 256 : 18 * 256);
            sheet.createFreezePane(0, 1);
            // Only the count column can be edited, so codes stay intact for the upload.
            sheet.protectSheet("");
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("تعذر إنشاء ملف الجرد.", e);
        }
    }

    /** Reads a filled count sheet; blank count cells are skipped. */
    public ImportResult importCounts(Long id, MultipartFile file) {
        Stocktake s = findOpen(id);
        validateFile(file);
        List<String> unknown = new ArrayList<>();
        List<String> outOfScope = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        int filled = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = sheet.getRow(0);
            int codeCol = column(header, fmt, H_CODE);
            int countCol = column(header, fmt, H_COUNT);
            if (codeCol < 0 || countCol < 0) {
                throw new IllegalArgumentException("الملف غير مطابق لملف الجرد. نزّل ملف الجرد من النظام واكتب الكميات في عمود «" + H_COUNT + "».");
            }
            if (sheet.getLastRowNum() > MAX_SHEET_ROWS) {
                throw new IllegalArgumentException("الملف يحتوي على أكثر من " + MAX_SHEET_ROWS + " صف.");
            }

            Map<String, ProductVariant> byCode = new HashMap<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String code = fmt.formatCellValue(row.getCell(codeCol)).trim();
                String raw = fmt.formatCellValue(row.getCell(countCol)).trim();
                if (raw.isEmpty()) continue;
                String where = "الصف " + (i + 1) + (code.isEmpty() ? "" : " (" + code + ")");
                Integer qty = parseCount(raw);
                if (code.isEmpty() || qty == null) {
                    invalid.add(where + ": " + (code.isEmpty() ? "الكود فارغ" : "الكمية «" + raw + "» غير صحيحة"));
                    continue;
                }
                ProductVariant v = byCode.computeIfAbsent(code, c -> variants.findByCode(c).orElse(null));
                if (v == null) {
                    unknown.add(where);
                    continue;
                }
                try {
                    upsert(s, v, qty);
                    filled++;
                } catch (IllegalArgumentException e) {
                    outOfScope.add(where + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("تعذر قراءة ملف Excel.");
        }

        activityLog.record(ActivityEntity.STOCKTAKE, ActivityAction.IMPORT, s.getId(), label(s),
                "رفع ملف عد لجرد «" + s.getTitle() + "»",
                Changes.of().set("أصناف تم عدها", filled).set("أكواد غير معروفة", unknown.size())
                        .set("خارج النطاق", outOfScope.size()).set("قيم غير صحيحة", invalid.size()));
        return new ImportResult(filled, unknown, outOfScope, invalid);
    }

    // ------------------------------------------------------------------ helpers

    private Stocktake find(Long id) {
        return stocktakes.findById(id).orElseThrow(() -> new ResourceNotFoundException("الجرد رقم " + id + " غير موجود."));
    }

    private Stocktake findOpen(Long id) {
        Stocktake s = find(id);
        if (!s.isOpen()) {
            throw new ConflictException("هذا الجرد " + (s.getStatus() == StocktakeStatus.APPROVED ? "معتمد" : "ملغي") + " ولا يمكن تعديله.");
        }
        return s;
    }

    private List<ProductVariant> countable(Stocktake s) {
        List<String> scope = s.scope();
        return scope.isEmpty() ? variants.findAllCountable() : variants.findCountableByTypes(scope);
    }

    private static StocktakeLine lineFor(Stocktake s, Long variantId) {
        return s.getLines().stream().filter(l -> l.getVariant().getId().equals(variantId)).findFirst().orElse(null);
    }

    private static Summary summary(Stocktake s, int totalInScope) {
        return new Summary(s.getId(), s.getCode(), s.getTitle(), s.scope(), s.getStatus(), s.getNotes(),
                s.getCreatedAt(), name(s.getCreatedBy()), s.getApprovedAt(), name(s.getApprovedBy()),
                s.getLines().size(), totalInScope,
                s.getShortageCost(), s.getSurplusCost(), s.getShortageSell(), s.getSurplusSell());
    }

    private static CountLine countLine(StocktakeLine l) {
        ProductVariant v = l.getVariant();
        Product p = l.getProduct();
        return new CountLine(v.getId(), p.getId(), p.getName(), p.getItemType(), v.getCode(), v.getBarcode(),
                v.getColor(), v.getSize(), v.label(), l.getCountedQty(), l.getCountedAt());
    }

    /** Open stocktakes are valued at today's prices, approved ones at the prices stored on approval. */
    private static ReviewLine reviewLine(StocktakeLine l, boolean open) {
        Product p = l.getProduct();
        ProductVariant v = l.getVariant();
        BigDecimal cost = open || l.getUnitCost() == null ? p.getPurchasePrice() : l.getUnitCost();
        BigDecimal price = open || l.getUnitPrice() == null ? p.getSellingPrice() : l.getUnitPrice();
        int variance = l.variance();
        return new ReviewLine(v.getId(), p.getId(), p.getName(), p.getItemType(), v.getCode(), v.label(),
                l.getSystemQtyAtCount(), l.getCountedQty(), variance, cost, price,
                value(cost, variance), value(price, variance),
                l.getReason(), l.getNote(), l.getAppliedVariance(), Boolean.TRUE.equals(l.getClamped()));
    }

    private static BigDecimal value(BigDecimal unit, int qty) {
        return unit == null ? null : unit.multiply(BigDecimal.valueOf(qty)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private record Totals(BigDecimal shortageCost, BigDecimal surplusCost, BigDecimal shortageSell, BigDecimal surplusSell) {
    }

    /** Shortages as positive amounts; products without a price count as 0. */
    private static Totals totals(List<ReviewLine> lines) {
        Function<Function<ReviewLine, BigDecimal>, BigDecimal[]> sum = f -> {
            BigDecimal shortage = BigDecimal.ZERO, surplus = BigDecimal.ZERO;
            for (ReviewLine l : lines) {
                BigDecimal v = f.apply(l);
                if (v == null) continue;
                if (v.signum() < 0) shortage = shortage.add(v.negate());
                else surplus = surplus.add(v);
            }
            return new BigDecimal[]{shortage, surplus};
        };
        BigDecimal[] cost = sum.apply(ReviewLine::costValue);
        BigDecimal[] sell = sum.apply(ReviewLine::sellValue);
        return new Totals(cost[0], cost[1], sell[0], sell[1]);
    }

    private static int column(Row header, DataFormatter fmt, String name) {
        if (header == null) return -1;
        for (Cell c : header) {
            if (name.equals(fmt.formatCellValue(c).trim())) return c.getColumnIndex();
        }
        return -1;
    }

    /** Whole numbers 0..MAX_COUNT; Excel may give "5" or "5.0". */
    static Integer parseCount(String raw) {
        try {
            BigDecimal d = new BigDecimal(raw.replace(",", ""));
            if (d.signum() < 0 || d.stripTrailingZeros().scale() > 0 || d.compareTo(BigDecimal.valueOf(MAX_COUNT)) > 0) return null;
            return d.intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("اختر ملف Excel لرفعه.");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new IllegalArgumentException("نوع الملف غير مدعوم. استخدم ملف Excel بصيغة .xlsx");
        }
        if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("حجم الملف أكبر من 5 ميجابايت.");
    }

    private static String label(Stocktake s) {
        return s.getTitle() + " (" + s.getCode() + ")";
    }

    private static String scopeLabel(Stocktake s) {
        List<String> scope = s.scope();
        return scope.isEmpty() ? "كل المنتجات" : scope.stream().map(String::valueOf).collect(Collectors.joining("، "));
    }

    private static String name(User u) {
        return u == null ? null : (u.getFullName() != null ? u.getFullName() : u.getUsername());
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
