package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.dto.product.CreateProductPriceRequest;
import Elramy.Group.MafroshartElramyz.dto.product.CreateProductRequest;
import Elramy.Group.MafroshartElramyz.dto.product.ProductCatalog;
import Elramy.Group.MafroshartElramyz.dto.product.ProductImportResult;
import Elramy.Group.MafroshartElramyz.dto.product.ProductImportRowError;
import Elramy.Group.MafroshartElramyz.dto.product.ProductResponse;
import Elramy.Group.MafroshartElramyz.exception.ProductImportValidationException;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductDraftRepository;
import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.models.ProductDraft;
import Elramy.Group.MafroshartElramyz.dto.product.ProductImportSkippedRow;
import Elramy.Group.MafroshartElramyz.dto.product.ProductFieldRules;
import Elramy.Group.MafroshartElramyz.dto.product.ProductDraftResponse;
import Elramy.Group.MafroshartElramyz.dto.product.ProductVariantResponse;
import Elramy.Group.MafroshartElramyz.dto.product.ProductVariantRequest;
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
import java.math.RoundingMode;
import java.util.*;

/**
 * Bulk product import from an Excel sheet.
 *
 * <p>The import is all-or-nothing: every row is validated first, and if
 * any row has a problem nothing is saved and every problem is reported.
 * That way a corrected file can simply be uploaded again without creating
 * half a batch or duplicates.
 */
@Service
@RequiredArgsConstructor
public class ProductImportServiceImpl implements ProductImportService {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final ProductDraftRepository draftRepository;
    private final UserService userService;
    private final ActivityLogService activityLog;
    private final Elramy.Group.MafroshartElramyz.producttype.ItemTypeService itemTypes;

    /** Ledger reference for opening stock entered through the import. */
    static final String REFERENCE_PRODUCT_IMPORT = "PRODUCT_IMPORT";

    static final String DATA_SHEET = "المنتجات";
    static final String HELP_SHEET = "التعليمات";
    static final String LISTS_SHEET = "القوائم";

    static final int MAX_ROWS = 1000;
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    /** Cap on reported errors, so a wrong file can't produce a huge response. */
    static final int MAX_REPORTED_ERRORS = 300;

    static final BigDecimal MAX_PRICE = new BigDecimal("10000000");
    static final BigDecimal MAX_PROFIT = ProductFieldRules.MAX_PROFIT;
    static final int MAX_MIN_QUANTITY = 1_000_000;
    static final int MAX_QUANTITY = 1_000_000;

    /** Sheet columns, in template order. */
    enum Col {
        NAME("الاسم", true, 30),
        TYPE("النوع", true, 16),
        COLOR("اللون", false, 16),
        SIZE("المقاس", false, 16),
        MODEL("الموديل", false, 18),
        QUANTITY("الكمية الحالية", true, 16),
        MIN_QTY("الحد الأدنى للكمية", false, 18),
        PURCHASE_PRICE("سعر التكلفة", true, 14),
        PROFIT("نسبة الربح %", false, 14),
        SELLING_PRICE("سعر البيع", false, 14);

        final String header;
        final boolean required;
        final int width;

        Col(String header, boolean required, int width) {
            this.header = header;
            this.required = required;
            this.width = width;
        }

        String title() {
            return required ? header + " *" : header;
        }
    }


    // =========================================================
    // TEMPLATE
    // =========================================================

    @Override
    public byte[] buildTemplate() {

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet data = wb.createSheet(DATA_SHEET);
            Sheet help = wb.createSheet(HELP_SHEET);
            Sheet lists = wb.createSheet(LISTS_SHEET);
            data.setRightToLeft(true);
            help.setRightToLeft(true);
            lists.setRightToLeft(true);

            // --- Header row ---
            CellStyle required = headerStyle(wb, IndexedColors.LIGHT_ORANGE);
            CellStyle optional = headerStyle(wb, IndexedColors.GREY_25_PERCENT);
            Row header = data.createRow(0);
            header.setHeightInPoints(24);
            for (Col c : Col.values()) {
                Cell cell = header.createCell(c.ordinal());
                cell.setCellValue(c.title());
                cell.setCellStyle(c.required ? required : optional);
                data.setColumnWidth(c.ordinal(), c.width * 256);
            }
            data.createFreezePane(0, 1);

            // Text columns stay text, so e.g. a model "0123" keeps its zero.
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));
            data.setDefaultColumnStyle(Col.NAME.ordinal(), text);
            data.setDefaultColumnStyle(Col.MODEL.ordinal(), text);

            // --- Dropdown source lists (hidden sheet + named ranges) ---
            writeList(wb, lists, 0, "ProductTypes", itemTypes.activeNames());
            writeList(wb, lists, 1, "ProductColors", new ArrayList<>(ProductCatalog.COLORS.keySet()));
            wb.setSheetHidden(wb.getSheetIndex(lists), true);

            // --- In-sheet validation (catches mistakes while typing) ---
            DataValidationHelper dv = data.getDataValidationHelper();
            listValidation(data, dv, Col.TYPE, "ProductTypes", "اختر النوع من القائمة");
            listValidation(data, dv, Col.COLOR, "ProductColors", "اختر اللون من القائمة");
            addValidation(data, dv.createIntegerConstraint(
                            DataValidationConstraint.OperatorType.BETWEEN, "0", String.valueOf(MAX_MIN_QUANTITY)),
                    dv, Col.MIN_QTY, "الحد الأدنى يجب أن يكون رقماً صحيحاً 0 أو أكثر");
            addValidation(data, dv.createIntegerConstraint(
                            DataValidationConstraint.OperatorType.BETWEEN, "0", String.valueOf(MAX_QUANTITY)),
                    dv, Col.QUANTITY, "الكمية يجب أن تكون رقماً صحيحاً 0 أو أكثر");
            for (Col c : List.of(Col.PURCHASE_PRICE, Col.SELLING_PRICE)) {
                addValidation(data, dv.createDecimalConstraint(
                                DataValidationConstraint.OperatorType.BETWEEN, "0.01", MAX_PRICE.toPlainString()),
                        dv, c, "السعر يجب أن يكون رقماً أكبر من صفر");
            }
            addValidation(data, dv.createDecimalConstraint(
                            DataValidationConstraint.OperatorType.BETWEEN, "0", MAX_PROFIT.toPlainString()),
                    dv, Col.PROFIT, "نسبة الربح يجب أن تكون رقماً بين 0 و " + MAX_PROFIT.toPlainString());
            addValidation(data, dv.createTextLengthConstraint(
                            DataValidationConstraint.OperatorType.LESS_OR_EQUAL, "200", null),
                    dv, Col.NAME, "الاسم يجب ألا يزيد عن 200 حرف");
            addValidation(data, dv.createTextLengthConstraint(
                            DataValidationConstraint.OperatorType.LESS_OR_EQUAL, "100", null),
                    dv, Col.MODEL, "الموديل يجب ألا يزيد عن 100 حرف");

            writeHelp(wb, help);

            wb.setActiveSheet(0);
            wb.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Failed to build product template.", e);
        }
    }

    private CellStyle headerStyle(Workbook wb, IndexedColors fill) {
        Font font = wb.createFont();
        font.setBold(true);
        CellStyle s = wb.createCellStyle();
        s.setFont(font);
        s.setFillForegroundColor(fill.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private void writeList(Workbook wb, Sheet sheet, int col, String name, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            Row r = sheet.getRow(i) != null ? sheet.getRow(i) : sheet.createRow(i);
            r.createCell(col).setCellValue(values.get(i));
        }
        Name n = wb.createName();
        n.setNameName(name);
        String letter = String.valueOf((char) ('A' + col));
        n.setRefersToFormula("'" + LISTS_SHEET + "'!$" + letter + "$1:$" + letter + "$" + values.size());
    }

    private void listValidation(Sheet sheet, DataValidationHelper dv, Col col, String rangeName, String error) {
        addValidation(sheet, dv.createFormulaListConstraint(rangeName), dv, col, error);
    }

    private void addValidation(Sheet sheet, DataValidationConstraint constraint, DataValidationHelper dv, Col col, String error) {
        CellRangeAddressList range = new CellRangeAddressList(1, MAX_ROWS, col.ordinal(), col.ordinal());
        DataValidation v = dv.createValidation(constraint, range);
        v.setShowErrorBox(true);
        v.setErrorStyle(DataValidation.ErrorStyle.STOP);
        v.createErrorBox("قيمة غير صحيحة", error);
        v.setEmptyCellAllowed(true);
        sheet.addValidationData(v);
    }

    private void writeHelp(Workbook wb, Sheet help) {
        Font bold = wb.createFont();
        bold.setBold(true);
        CellStyle title = wb.createCellStyle();
        title.setFont(bold);

        String[] lines = {
                "طريقة إضافة المنتجات من ملف Excel",
                "",
                "1. اكتب المنتجات في ورقة \"" + DATA_SHEET + "\"، منتج واحد في كل صف، بدءاً من الصف الثاني.",
                "2. لا تغيّر أسماء الأعمدة في الصف الأول ولا تحذفها.",
                "3. المنتج المكتمل هو الذي يحتوي على الأعمدة المميزة بعلامة *: الاسم، النوع، الكمية الحالية، وسعر التكلفة مع نسبة الربح أو سعر البيع.",
                "   اللون والمقاس والموديل والحد الأدنى للكمية اختيارية.",
                "4. كل صف يمثل لوناً/مقاساً واحداً. الصفوف التي لها نفس الاسم والنوع والموديل تُجمع في منتج واحد بعدة ألوان ومقاسات،",
                "   ولكل لون/مقاس كميته وكوده وباركوده. السعر واحد لكل ألوان ومقاسات المنتج (يؤخذ من أول صف).",
                "   إذا كان المنتج موجوداً بالفعل في النظام يُضاف إليه اللون/المقاس الجديد.",
                "5. المنتجات المكتملة تُضاف مباشرة. الناقصة تُحفظ في \"منتجات غير مكتملة\" ولا تظهر في النظام حتى يتم إكمالها.",
                "6. النوع واللون يجب اختيارهما من القوائم المنسدلة (القيمة غير الموجودة تُترك فارغة ويُحفظ الصف كغير مكتمل). المقاس نص حر حتى " + ProductCatalog.MAX_SIZE_LENGTH + " حرف (مثال: 200x230 أو طقم 4).",
                "7. أي قيمة غير صحيحة (سعر ليس رقماً، كمية سالبة، اسم طويل جداً...) تُترك فارغة مع ملاحظة توضح القيمة الأصلية.",
                "8. الكمية الحالية هي المخزون الموجود الآن لهذا اللون/المقاس (رقم صحيح، 0 مقبول)، وتُسجَّل كرصيد افتتاحي.",
                "   الحد الأدنى للكمية للتنبيه عندما يصل إجمالي المنتج (كل الألوان والمقاسات) إليه.",
                "9. اللون/المقاس المكرر (في الملف، أو الموجود بالفعل في المنتج، أو في المنتجات غير المكتملة) يتم تخطيه؛ لزيادة كميته استخدم فاتورة شراء.",
                "10. الحد الأقصى " + MAX_ROWS + " منتج في الملف، وحجم الملف حتى 5 ميجابايت.",
                "11. يتم إنشاء كود المنتج والباركود تلقائياً.",
                "12. لزيادة مخزون منتج موجود بالفعل استخدم فاتورة شراء، وليس هذا الملف.",
                "",
                "مثال:",
        };
        int r = 0;
        for (String line : lines) {
            Cell c = help.createRow(r++).createCell(0);
            c.setCellValue(line);
            if (r == 1 || line.equals("مثال:")) c.setCellStyle(title);
        }
        Row h = help.createRow(r++);
        for (Col col : Col.values()) {
            Cell c = h.createCell(col.ordinal());
            c.setCellValue(col.title());
            c.setCellStyle(title);
        }
        String[][] examples = {
                {"لحاف قطن سادة", "لحاف", "أبيض", "كينج", "L-200", "20", "5", "450", "30", ""},
                {"طقم ملايات مشجر", "طقم", "وردي", "طقم 4", "", "8", "3", "600", "", "850"},
                {"فوطة حمام", "فوط", "كحلي", "وسط", "", "", "10", "", "", ""},
        };
        for (String[] ex : examples) {
            Row row = help.createRow(r++);
            for (int i = 0; i < ex.length; i++) row.createCell(i).setCellValue(ex[i]);
        }
        help.setColumnWidth(0, 30 * 256);
        for (Col col : Col.values()) {
            if (col.ordinal() > 0) help.setColumnWidth(col.ordinal(), col.width * 256);
        }
    }


    // =========================================================
    // IMPORT
    // =========================================================

    /**
     * One sheet row, read leniently: every field is either a valid value or
     * null. Values that were present but invalid are cleared and explained
     * in {@code notes}, so the row can still be saved as a draft.
     */
    private static final class ParsedRow {
        int rowNumber;
        String name;
        String type;
        String color;
        String size;
        String model;
        Integer quantity;
        Integer minimumQuantity;
        BigDecimal purchasePrice;
        BigDecimal profitPercentage;
        BigDecimal sellingPrice;
        final List<String> notes = new ArrayList<>();
        /** A colour/size was given but not in the list: must not import as "none". */
        boolean invalidColor;
        boolean invalidSize;

        List<String> missing() {
            List<String> m = new ArrayList<>(ProductFieldRules.missingFields(name, type, purchasePrice, profitPercentage, sellingPrice, quantity));
            if (invalidColor) m.add(ProductFieldRules.COLOR);
            if (invalidSize) m.add(ProductFieldRules.SIZE);
            return m;
        }

        /** Same name + type + model = same product. */
        String productKey() {
            return ProductFieldRules.productKey(name, type, model);
        }

        /** Product + colour + size = one variant. */
        String variantKey() {
            return productKey() + "#" + ProductFieldRules.comboKey(color, size);
        }

        boolean identifiable() {
            return ProductFieldRules.identifiable(name, type);
        }
    }

    /**
     * Complete rows become products; incomplete rows become drafts
     * ("منتجات غير مكتملة"); duplicates are skipped. Only problems with the
     * file itself reject the upload. One transaction for the whole file.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductImportResult importFile(MultipartFile file) {

        validateFile(file);

        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {

            Sheet sheet = wb.getSheet(DATA_SHEET) != null ? wb.getSheet(DATA_SHEET) : wb.getSheetAt(0);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter();

            List<ProductImportRowError> fileErrors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            Map<Col, Integer> columns = mapHeader(sheet, formatter, evaluator, fileErrors, warnings);
            if (!fileErrors.isEmpty()) {
                throw new ProductImportValidationException("ملف غير مطابق للقالب. استخدم القالب الذي يتم تنزيله من النظام.", fileErrors);
            }

            // Read every row first, so a file over the limit is rejected
            // before anything is saved.
            List<ParsedRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlank(row, columns, formatter, evaluator)) continue;
                if (rows.size() == MAX_ROWS) {
                    throw new ProductImportValidationException("الملف يحتوي على أكثر من " + MAX_ROWS + " منتج.",
                            List.of(new ProductImportRowError(i + 1, null, "قسّم الملف إلى أكثر من ملف (الحد الأقصى " + MAX_ROWS + " منتج).")));
                }
                rows.add(parseRow(row, i + 1, columns, formatter, evaluator));
            }
            if (rows.isEmpty()) {
                throw new ProductImportValidationException("الملف لا يحتوي على أي منتجات.",
                        List.of(new ProductImportRowError(2, null, "اكتب المنتجات بدءاً من الصف الثاني في ورقة \"" + DATA_SHEET + "\"")));
            }

            // Existing products by identity (name + type + model), and the
            // colour/size combinations each already has.
            Map<String, Product> productsByKey = new HashMap<>();
            Map<String, String> existingVariantOwner = new HashMap<>();   // variantKey -> product code
            for (Product p : productRepository.findAll()) {
                String pk = ProductFieldRules.productKey(p.getName(), p.getItemType(), p.getModel());
                Product prev = productsByKey.get(pk);
                if (prev == null || (Boolean.FALSE.equals(prev.getActive()) && !Boolean.FALSE.equals(p.getActive()))) {
                    productsByKey.put(pk, p);   // prefer an active product
                }
                for (var v : p.activeVariants()) {
                    existingVariantOwner.put(pk + "#" + ProductFieldRules.comboKey(v.getColor(), v.getSize()), p.getCode());
                }
            }
            Set<String> drafted = new HashSet<>();
            for (ProductDraft d : draftRepository.findAll()) {
                if (ProductFieldRules.identifiable(d.getName(), d.getItemType())) {
                    String pk = ProductFieldRules.productKey(d.getName(), d.getItemType(), d.getModel());
                    for (var v : d.getVariants()) drafted.add(pk + "#" + ProductFieldRules.comboKey(v.getColor(), v.getSize()));
                }
            }
            // Incomplete rows of the same product (name + type + model) in
            // this file become ONE draft with several colours/sizes.
            Map<String, ProductDraft> draftByProduct = new LinkedHashMap<>();
            List<ProductDraft> newDrafts = new ArrayList<>();

            Map<String, Integer> firstRow = new HashMap<>();            // variantKey -> row in this file
            Map<String, Long> productIdByKey = new HashMap<>();         // products created/used in this run
            Map<Long, ProductResponse> touched = new LinkedHashMap<>(); // products to report
            Set<Long> newProducts = new HashSet<>();

            List<ProductDraftResponse> drafts = new ArrayList<>();
            List<ProductImportSkippedRow> skipped = new ArrayList<>();
            int withPrice = 0;
            int variantsAdded = 0;
            int withStock = 0;
            long totalQuantity = 0;
            Long userId = currentUserId();
            String sourceFile = Optional.ofNullable(file.getOriginalFilename()).orElse(null);

            for (ParsedRow r : rows) {
                if (r.identifiable()) {
                    String vk = r.variantKey();
                    Integer first = firstRow.putIfAbsent(vk, r.rowNumber);
                    if (first != null) {
                        skipped.add(new ProductImportSkippedRow(r.rowNumber, r.name, "مكرر في الملف (نفس المنتج واللون والمقاس في الصف " + first + ")"));
                        continue;
                    }
                    String owner = existingVariantOwner.get(vk);
                    if (owner != null) {
                        skipped.add(new ProductImportSkippedRow(r.rowNumber, r.name, "اللون/المقاس موجود بالفعل في المنتج " + owner
                                + "، لزيادة الكمية استخدم فاتورة شراء"));
                        continue;
                    }
                    if (drafted.contains(vk)) {
                        skipped.add(new ProductImportSkippedRow(r.rowNumber, r.name, "موجود بالفعل في المنتجات غير المكتملة"));
                        continue;
                    }
                    Product match = productsByKey.get(r.productKey());
                    if (match != null && Boolean.FALSE.equals(match.getActive())) {
                        skipped.add(new ProductImportSkippedRow(r.rowNumber, r.name, "المنتج " + match.getCode() + " غير نشط، فعّله أولاً"));
                        continue;
                    }
                }

                if (!r.missing().isEmpty()) {
                    // Notes carry their row, since a draft can gather several rows.
                    List<String> rowNotes = r.notes.stream().map(n -> n + " (الصف " + r.rowNumber + ")").toList();
                    ProductDraft d = r.identifiable() ? draftByProduct.get(r.productKey()) : null;
                    if (d == null) {
                        d = ProductDraft.builder()
                                .name(r.name).itemType(r.type).model(r.model).minimumQuantity(r.minimumQuantity)
                                .purchasePrice(r.purchasePrice).profitPercentage(r.profitPercentage).sellingPrice(r.sellingPrice)
                                .sourceFile(sourceFile).sourceRow(r.rowNumber).createdBy(userId)
                                .build();
                        newDrafts.add(d);
                        if (r.identifiable()) draftByProduct.put(r.productKey(), d);
                    } else if (d.getPurchasePrice() == null && d.getSellingPrice() == null && d.getProfitPercentage() == null) {
                        // The product's price may only be on a later row.
                        d.setPurchasePrice(r.purchasePrice);
                        d.setProfitPercentage(r.profitPercentage);
                        d.setSellingPrice(r.sellingPrice);
                    }
                    d.addVariant(r.color, r.size, r.quantity, r.rowNumber);
                    if (!rowNotes.isEmpty()) {
                        d.setImportNotes((d.getImportNotes() == null ? "" : d.getImportNotes() + "\n") + String.join("\n", rowNotes));
                    }
                    continue;
                }

                // Complete row: new product, or a new colour/size of a product
                // that exists (in the system or earlier in this file).
                CreateProductPriceRequest price = ProductFieldRules.priceRequest(r.purchasePrice, r.profitPercentage, r.sellingPrice);
                String pk = r.productKey();
                Long productId = productIdByKey.get(pk);
                if (productId == null && productsByKey.containsKey(pk)) productId = productsByKey.get(pk).getId();

                ProductVariantResponse variant;
                ProductResponse product;
                if (productId == null) {
                    product = productService.create(new CreateProductRequest(
                            r.name, r.model, r.type, r.minimumQuantity != null ? r.minimumQuantity : 0,
                            List.of(new ProductVariantRequest(null, r.color, r.size))));
                    productId = product.id();
                    variant = product.variants().get(0);
                    product = productService.setPrice(productId, price);
                    withPrice++;
                    newProducts.add(productId);
                } else {
                    variant = productService.addVariant(productId, r.color, r.size);
                    variantsAdded++;
                    product = productService.getById(productId);
                    if (product.currentPrice() == null) {
                        product = productService.setPrice(productId, price);
                        withPrice++;
                    } else if (expectedSelling(price).compareTo(product.currentPrice().sellingPrice()) != 0) {
                        warnings.add("الصف " + r.rowNumber + ": سعر البيع (" + expectedSelling(price).toPlainString()
                                + ") مختلف عن سعر المنتج «" + product.name() + "» (" + product.currentPrice().sellingPrice().toPlainString()
                                + ")، تم استخدام سعر المنتج لأن السعر واحد لكل الألوان والمقاسات.");
                    }
                }
                productIdByKey.put(pk, productId);

                if (r.quantity > 0) {
                    // Through StockService so the opening stock shows up in
                    // the movement ledger like any other stock-in.
                    stockService.increaseStock(variant.id(), r.quantity, TransactionType.STOCK_IN, price.purchasePrice(),
                            null, REFERENCE_PRODUCT_IMPORT, "رصيد افتتاحي من ملف Excel (الصف " + r.rowNumber + ")");
                    withStock++;
                    totalQuantity += r.quantity;
                }
                touched.put(productId, null);
            }

            for (ProductDraft d : newDrafts) {
                // The sheet has one quantity per colour/size row, so the draft's
                // total is their sum - known only when every row has one.
                if (d.getVariants().stream().allMatch(v -> v.getQuantity() != null)) {
                    d.setTotalQuantity(d.getVariants().stream().mapToInt(v -> v.getQuantity()).sum());
                }
                drafts.add(ProductDraftServiceImpl.toResponse(draftRepository.save(d)));
            }

            List<ProductResponse> products = new ArrayList<>();
            for (Long id : touched.keySet()) products.add(productService.getById(id));

            activityLog.record(ActivityEntity.PRODUCT, ActivityAction.IMPORT, null, sourceFile,
                    "استيراد منتجات من ملف Excel" + (sourceFile != null ? " «" + sourceFile + "»" : ""),
                    Changes.of().set("منتجات جديدة", newProducts.size()).set("ألوان/مقاسات أُضيفت لمنتجات موجودة", variantsAdded)
                            .set("منتجات غير مكتملة", drafts.size()).set("صفوف تم تخطيها", skipped.size())
                            .set("إجمالي الكمية الافتتاحية", totalQuantity));
            return new ProductImportResult(newProducts.size(), withPrice, withStock, totalQuantity, products,
                    variantsAdded, drafts.size(), drafts, skipped, warnings);

        } catch (IOException | org.apache.poi.EmptyFileException | org.apache.poi.UnsupportedFileFormatException e) {
            throw new IllegalArgumentException("تعذر قراءة الملف. تأكد أنه ملف Excel صالح (.xlsx).");
        }
    }

    /** The selling price a price request results in (for comparisons). */
    private static BigDecimal expectedSelling(CreateProductPriceRequest p) {
        if (p.sellingPrice() != null) return p.sellingPrice().setScale(2, RoundingMode.HALF_UP);
        return p.purchasePrice().multiply(BigDecimal.ONE.add(p.profitPercentage().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Long currentUserId() {
        try {
            User u = userService.getCurrentUser();
            return u != null ? u.getId() : null;
        } catch (RuntimeException e) {
            return null; // no authenticated user (e.g. tests); the draft just has no author
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("اختر ملف Excel لرفعه.");
        }
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new IllegalArgumentException("نوع الملف غير مدعوم. استخدم ملف Excel بصيغة .xlsx");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("حجم الملف أكبر من 5 ميجابايت.");
        }
    }

    /**
     * Finds each column by its header text, so column order doesn't matter.
     * Only a missing name column rejects the file; any other missing column
     * just leaves that field empty (rows become drafts).
     */
    private Map<Col, Integer> mapHeader(Sheet sheet, DataFormatter f, FormulaEvaluator ev,
                                        List<ProductImportRowError> errors, List<String> warnings) {
        Map<Col, Integer> map = new EnumMap<>(Col.class);
        Row header = sheet.getRow(0);
        if (header == null) {
            errors.add(new ProductImportRowError(1, null, "الصف الأول (أسماء الأعمدة) غير موجود."));
            return map;
        }
        for (Cell cell : header) {
            String title = ProductCatalog.normalize(f.formatCellValue(cell, ev)).replace("*", "").trim();
            for (Col c : Col.values()) {
                if (ProductCatalog.normalize(c.header).equals(title)) map.putIfAbsent(c, cell.getColumnIndex());
            }
        }
        if (!map.containsKey(Col.NAME)) {
            errors.add(new ProductImportRowError(1, Col.NAME.header, "العمود \"" + Col.NAME.header + "\" غير موجود في الصف الأول."));
        }
        for (Col c : Col.values()) {
            if (c != Col.NAME && !map.containsKey(c)) {
                warnings.add("العمود \"" + c.header + "\" غير موجود في الملف، لذلك تُرك فارغاً لكل المنتجات.");
            }
        }
        return map;
    }

    /** Reads a row leniently: invalid values are cleared and noted, never rejected. */
    private ParsedRow parseRow(Row row, int rowNumber, Map<Col, Integer> cols, DataFormatter f, FormulaEvaluator ev) {
        ParsedRow r = new ParsedRow();
        r.rowNumber = rowNumber;

        String name = text(row, cols, Col.NAME, f, ev);
        if (name.length() > ProductFieldRules.MAX_NAME) {
            note(r, Col.NAME, "أطول من " + ProductFieldRules.MAX_NAME + " حرف", name.substring(0, 40) + "…");
        } else if (!name.isEmpty()) {
            r.name = name;
        }

        String typeRaw = text(row, cols, Col.TYPE, f, ev);
        if (!typeRaw.isEmpty()) {
            r.type = itemTypes.resolve(typeRaw).orElse(null);
            if (r.type == null) note(r, Col.TYPE, "غير موجود في القائمة", typeRaw);
        }
        r.color = catalogValue(r, row, cols, Col.COLOR, ProductCatalog.COLORS, f, ev);
        // Size is free text; only a value too long for the column is
        // blanked, and the row then goes to the drafts like a bad colour.
        String sizeRaw = text(row, cols, Col.SIZE, f, ev);
        try {
            r.size = ProductCatalog.sizeText(sizeRaw);
        } catch (IllegalArgumentException e) {
            note(r, Col.SIZE, "أطول من " + ProductCatalog.MAX_SIZE_LENGTH + " حرف", sizeRaw.substring(0, 20) + "…");
            r.invalidSize = true;
        }

        String model = text(row, cols, Col.MODEL, f, ev);
        if (model.length() > ProductFieldRules.MAX_MODEL) {
            note(r, Col.MODEL, "أطول من " + ProductFieldRules.MAX_MODEL + " حرف", model.substring(0, 40) + "…");
        } else if (!model.isEmpty()) {
            r.model = model;
        }

        r.quantity = wholeNumber(r, row, cols, Col.QUANTITY, MAX_QUANTITY, f, ev);
        r.minimumQuantity = wholeNumber(r, row, cols, Col.MIN_QTY, MAX_MIN_QUANTITY, f, ev);

        parsePrice(r, row, cols, f, ev);
        return r;
    }

    /**
     * Keeps the valid parts of the price. When profit % and selling price
     * are both given but disagree, both are cleared (the cost stays) and
     * the row becomes a draft so a person picks the right one.
     */
    private void parsePrice(ParsedRow r, Row row, Map<Col, Integer> cols, DataFormatter f, FormulaEvaluator ev) {
        BigDecimal cost = number(r, row, cols, Col.PURCHASE_PRICE, f, ev);
        if (cost != null && (cost.signum() <= 0 || cost.compareTo(MAX_PRICE) > 0)) {
            note(r, Col.PURCHASE_PRICE, cost.signum() <= 0 ? "يجب أن يكون أكبر من صفر" : "كبير جداً", cost.toPlainString());
            cost = null;
        }
        BigDecimal profit = number(r, row, cols, Col.PROFIT, f, ev);
        if (profit != null && (profit.signum() < 0 || profit.compareTo(MAX_PROFIT) > 0)) {
            note(r, Col.PROFIT, "يجب أن تكون بين 0 و " + MAX_PROFIT.toPlainString(), profit.toPlainString());
            profit = null;
        }
        BigDecimal selling = number(r, row, cols, Col.SELLING_PRICE, f, ev);
        if (selling != null && (selling.signum() <= 0 || selling.compareTo(MAX_PRICE) > 0)) {
            note(r, Col.SELLING_PRICE, selling.signum() <= 0 ? "يجب أن يكون أكبر من صفر" : "كبير جداً", selling.toPlainString());
            selling = null;
        }

        if (cost != null && profit != null && selling != null) {
            BigDecimal c2 = cost.setScale(2, RoundingMode.HALF_UP);
            BigDecimal expected = c2.multiply(BigDecimal.ONE.add(profit.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);
            if (expected.subtract(selling.setScale(2, RoundingMode.HALF_UP)).abs().compareTo(new BigDecimal("0.01")) > 0) {
                r.notes.add(Col.SELLING_PRICE.header + " و" + Col.PROFIT.header + ": غير متطابقين (المتوقع " + expected.toPlainString()
                        + ")، تم مسحهما لتحديد السعر الصحيح");
                profit = null;
                selling = null;
            }
        }
        r.purchasePrice = cost == null ? null : cost.setScale(2, RoundingMode.HALF_UP);
        r.profitPercentage = profit == null ? null : profit.setScale(2, RoundingMode.HALF_UP);
        r.sellingPrice = selling == null ? null : selling.setScale(2, RoundingMode.HALF_UP);
    }

    private String catalogValue(ParsedRow r, Row row, Map<Col, Integer> cols, Col col, Map<String, String> catalog,
                                DataFormatter f, FormulaEvaluator ev) {
        String raw = text(row, cols, col, f, ev);
        if (raw.isEmpty()) return null;
        return ProductCatalog.resolve(catalog, raw).orElseGet(() -> {
            note(r, col, "غير موجود في القائمة", raw);
            if (col == Col.COLOR) r.invalidColor = true;
            if (col == Col.SIZE) r.invalidSize = true;
            return null;
        });
    }

    /** A blank cell is null (not given); otherwise a whole number between 0 and max. */
    private Integer wholeNumber(ParsedRow r, Row row, Map<Col, Integer> cols, Col col, int max,
                                DataFormatter f, FormulaEvaluator ev) {
        BigDecimal v = number(r, row, cols, col, f, ev);
        if (v == null) return null;
        if (v.signum() < 0 || v.stripTrailingZeros().scale() > 0) {
            note(r, col, "يجب أن يكون رقماً صحيحاً 0 أو أكثر", v.stripTrailingZeros().toPlainString());
            return null;
        }
        if (v.compareTo(BigDecimal.valueOf(max)) > 0) {
            note(r, col, "كبير جداً", v.toPlainString());
            return null;
        }
        return v.intValueExact();
    }

    private static void note(ParsedRow r, Col col, String why, String original) {
        r.notes.add(col.header + ": \"" + original + "\" " + why + "، تُرك فارغاً");
    }


    // =========================================================
    // CELL HELPERS
    // =========================================================

    private Cell cell(Row row, Map<Col, Integer> cols, Col col) {
        Integer idx = cols.get(col);
        return idx == null ? null : row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    private String text(Row row, Map<Col, Integer> cols, Col col, DataFormatter f, FormulaEvaluator ev) {
        Cell c = cell(row, cols, col);
        return c == null ? "" : ProductCatalog.normalize(f.formatCellValue(c, ev));
    }

    /**
     * Reads a numeric cell, also accepting numbers typed as text (incl.
     * Arabic digits). Text that is not a number is cleared and noted.
     */
    private BigDecimal number(ParsedRow r, Row row, Map<Col, Integer> cols, Col col, DataFormatter f, FormulaEvaluator ev) {
        Cell c = cell(row, cols, col);
        if (c == null) return null;
        CellType type = c.getCellType() == CellType.FORMULA ? ev.evaluateFormulaCell(c) : c.getCellType();
        if (type == CellType.NUMERIC) return BigDecimal.valueOf(c.getNumericCellValue());
        String shown = f.formatCellValue(c, ev);
        String raw = toLatinDigits(shown).trim()
                .replace(",", "").replace("،", "").replace("%", "").replace(" ", "");
        if (raw.isEmpty()) return null;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            note(r, col, "ليست رقماً", shown);
            return null;
        }
    }

    private static String toLatinDigits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + ch - '٠'));
            else if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + ch - '۰'));
            else if (ch == '٫') b.append('.');
            else b.append(ch);
        }
        return b.toString();
    }

    private boolean isBlank(Row row, Map<Col, Integer> cols, DataFormatter f, FormulaEvaluator ev) {
        for (Col c : Col.values()) {
            if (!text(row, cols, c, f, ev).isEmpty()) return false;
        }
        return true;
    }
}
