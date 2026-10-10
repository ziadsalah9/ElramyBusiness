package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.dto.product.CreateProductPriceRequest;
import Elramy.Group.MafroshartElramyz.dto.product.CreateProductRequest;
import Elramy.Group.MafroshartElramyz.dto.product.ProductImportResult;
import Elramy.Group.MafroshartElramyz.dto.product.ProductPriceResponse;
import Elramy.Group.MafroshartElramyz.dto.product.ProductResponse;
import Elramy.Group.MafroshartElramyz.dto.product.ProductVariantResponse;
import Elramy.Group.MafroshartElramyz.exception.ProductImportValidationException;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductDraft;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.repository.ProductDraftRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Lenient import with variants: one row = one colour/size; rows of the same
 * product (name + type + model) become one product with several variants.
 * Incomplete rows -> drafts; duplicates -> skipped; only file problems reject.
 */
class ProductImportServiceImplTest {

    private ProductService productService;
    private ProductRepository productRepository;
    private StockService stockService;
    private ProductDraftRepository draftRepository;
    private ProductImportServiceImpl service;
    private final List<ProductDraft> savedDrafts = new ArrayList<>();

    /** In-memory stand-in for the product service. */
    private final Map<Long, String> names = new HashMap<>();
    private final Map<Long, BigDecimal> prices = new HashMap<>();
    private final Map<Long, List<ProductVariantResponse>> variantsOf = new HashMap<>();
    private final AtomicLong productIds = new AtomicLong(100);
    private final AtomicLong variantIds = new AtomicLong(1000);

    private static final String[] TEMPLATE_HEADERS = {
            "الاسم *", "النوع *", "اللون", "المقاس", "الموديل", "الكمية الحالية *",
            "الحد الأدنى للكمية", "سعر التكلفة *", "نسبة الربح %", "سعر البيع"
    };

    /** Test sheets: different column order (matching is by header text). */
    private static final String[] HEADERS = {
            "الاسم *", "النوع *", "اللون", "المقاس", "الموديل", "الحد الأدنى للكمية",
            "سعر التكلفة", "نسبة الربح %", "سعر البيع", "الكمية الحالية"
    };

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        productRepository = mock(ProductRepository.class);
        stockService = mock(StockService.class);
        draftRepository = mock(ProductDraftRepository.class);
        when(productRepository.findAll()).thenReturn(List.of());
        when(draftRepository.findAll()).thenReturn(List.of());
        AtomicLong draftIds = new AtomicLong(500);
        when(draftRepository.save(any())).thenAnswer(inv -> {
            ProductDraft d = inv.getArgument(0);
            d.setId(draftIds.incrementAndGet());
            savedDrafts.add(d);
            return d;
        });
        when(productService.create(any())).thenAnswer(inv -> {
            CreateProductRequest r = inv.getArgument(0);
            long id = productIds.incrementAndGet();
            names.put(id, r.name());
            List<ProductVariantResponse> vs = new ArrayList<>();
            r.variants().forEach(v -> vs.add(variant(v.color(), v.size())));
            variantsOf.put(id, vs);
            return response(id);
        });
        when(productService.addVariant(anyLong(), any(), any())).thenAnswer(inv -> {
            ProductVariantResponse v = variant(inv.getArgument(1), inv.getArgument(2));
            variantsOf.computeIfAbsent(inv.getArgument(0), k -> new ArrayList<>()).add(v);
            return v;
        });
        when(productService.setPrice(anyLong(), any())).thenAnswer(inv -> {
            CreateProductPriceRequest p = inv.getArgument(1);
            BigDecimal selling = p.sellingPrice() != null ? p.sellingPrice()
                    : p.purchasePrice().multiply(BigDecimal.ONE.add(p.profitPercentage().movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
            prices.put(inv.getArgument(0), selling);
            return response(inv.getArgument(0));
        });
        when(productService.getById(anyLong())).thenAnswer(inv -> response(inv.getArgument(0)));
        service = new ProductImportServiceImpl(productService, productRepository, stockService, draftRepository, mock(UserService.class), mock(Elramy.Group.MafroshartElramyz.activity.ActivityLogService.class), Elramy.Group.MafroshartElramyz.producttype.TestItemTypes.mock(), Elramy.Group.MafroshartElramyz.productcolor.TestColors.mock());
    }

    private ProductVariantResponse variant(String color, String size) {
        long id = variantIds.incrementAndGet();
        return new ProductVariantResponse(id, "V-" + id, "B" + id, color, size, String.valueOf(color) + "/" + size, 0, true);
    }

    private ProductResponse response(long id) {
        BigDecimal selling = prices.get(id);
        ProductPriceResponse price = selling == null ? null
                : new ProductPriceResponse(id, id, BigDecimal.ONE, selling, BigDecimal.ZERO, true, null);
        return new ProductResponse(id, "PRD-" + id, names.getOrDefault(id, "n"), null, "لحاف", price, 0, true, 0, true,
                variantsOf.getOrDefault(id, List.of()));
    }

    // ---------------------------------------------------------------- template

    @Test
    void templateHasDataHelpAndHiddenListSheetsWithHeaders() throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(service.buildTemplate()))) {
            Sheet data = wb.getSheet("المنتجات");
            assertEquals(0, wb.getSheetIndex(data), "data sheet must be first");
            assertNotNull(wb.getSheet("التعليمات"));
            assertTrue(wb.isSheetHidden(wb.getSheetIndex("القوائم")));
            Row header = data.getRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) assertEquals(TEMPLATE_HEADERS[i], header.getCell(i).getStringCellValue());
            assertFalse(data.getDataValidations().isEmpty());
            assertNotNull(wb.getName("ProductTypes"));
        }
    }

    @Test
    void untouchedTemplateIsRejectedAsEmpty() {
        ProductImportValidationException ex = assertThrows(ProductImportValidationException.class,
                () -> service.importFile(xlsx(service.buildTemplate())));
        assertTrue(ex.getMessage().contains("لا يحتوي"));
    }

    // ---------------------------------------------------------------- variants

    @Test
    void rowsOfTheSameProductBecomeOneProductWithSeveralVariants() throws Exception {
        // The user's example: 10 units = 3 large + 4 red/small + 3 yellow/medium.
        byte[] file = sheet(
                row("لحاف قطن", "لحاف", null, "كبير", "Q1", 2, 100, 30, null, 3),
                row("لحاف قطن", "لحاف", "أحمر", "صغير", "Q1", null, 100, 30, null, 4),
                row("لحاف قطن", "لحاف", "أصفر", "وسط", "Q1", null, 100, 30, null, 3)
        );

        ProductImportResult result = service.importFile(xlsx(file));

        assertEquals(1, result.created(), "one product");
        assertEquals(2, result.variantsAdded());
        assertEquals(1, result.products().size());
        assertEquals(3, result.products().get(0).variants().size());
        assertEquals(10, result.totalQuantity());
        assertTrue(result.warnings().isEmpty());
        verify(productService, times(1)).create(any());
        verify(productService, times(1)).setPrice(anyLong(), any());

        // Each colour/size gets its own opening stock on its own variant.
        ArgumentCaptor<Long> variantId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> qty = ArgumentCaptor.forClass(Integer.class);
        verify(stockService, times(3)).increaseStock(variantId.capture(), qty.capture(), eq(TransactionType.STOCK_IN),
                any(), isNull(), eq("PRODUCT_IMPORT"), anyString());
        assertEquals(3, new HashSet<>(variantId.getAllValues()).size());
        assertEquals(List.of(3, 4, 3), qty.getAllValues());
    }

    @Test
    void differentPriceOnALaterRowKeepsTheProductPriceAndWarns() throws Exception {
        ProductImportResult result = service.importFile(xlsx(sheet(
                row("لحاف", "لحاف", "أحمر", null, null, null, 100, null, 150, 1),
                row("لحاف", "لحاف", "أصفر", null, null, null, 100, null, 175, 1)
        )));
        assertEquals(1, result.created());
        assertEquals(1, result.variantsAdded());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("الصف 3"));
        verify(productService, times(1)).setPrice(anyLong(), any());
    }

    @Test
    void newColourOfAnExistingProductIsAddedButAnExistingColourIsSkipped() throws Exception {
        Product existing = Product.builder().id(7L).code("PRD-OLD").name("لحاف قطن").itemType("لحاف").active(true).build();
        existing.getVariants().add(ProductVariant.builder().id(70L).product(existing).color("أبيض").size("كينج").code("PRD-OLD-1").quantity(5).build());
        when(productRepository.findAll()).thenReturn(List.of(existing));
        names.put(7L, "لحاف قطن");
        prices.put(7L, new BigDecimal("130.00"));

        ProductImportResult result = service.importFile(xlsx(sheet(
                row("  لحاف   قطن ", "لحاف", "أبيض", "كينج", null, null, 100, 30, null, 2),   // same colour/size -> skip
                row("لحاف قطن", "لحاف", "أحمر", "كينج", null, null, 100, 30, null, 6)         // new colour -> add
        )));

        assertEquals(0, result.created());
        assertEquals(1, result.variantsAdded());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("PRD-OLD"));
        assertTrue(result.skipped().get(0).reason().contains("فاتورة شراء"));
        verify(productService, never()).create(any());
        verify(productService).addVariant(7L, "أحمر", "كينج");
        verify(stockService).increaseStock(anyLong(), eq(6), eq(TransactionType.STOCK_IN), any(), isNull(), eq("PRODUCT_IMPORT"), anyString());
    }

    @Test
    void sameColourSizeTwiceInTheFileIsSkipped() throws Exception {
        ProductImportResult result = service.importFile(xlsx(sheet(
                row("كامل", "لحاف", "أبيض", "كينج", null, null, 100, 20, null, 4),
                row("ناقص", "لحاف", "أبيض", "كينج", null, null, null, null, null, null),
                row("كامل", "لحاف", "أبيض", "كينج", null, null, 100, 20, null, 4)
        )));
        assertEquals(1, result.created());
        assertEquals(1, result.drafted());
        assertEquals(1, result.skipped().size());
        assertEquals(4, result.skipped().get(0).row());
        assertTrue(result.skipped().get(0).reason().contains("الصف 2"));
    }

    // ---------------------------------------------------------------- complete rows

    @Test
    void completeRowsResolveLabelsArabicDigitsAndSendOnePriceField() throws Exception {
        ProductImportResult result = service.importFile(xlsx(sheet(
                row("طقم مشجر", "طقم", "وردي", "طقم 4 قطع", null, null, 600, null, 850, 0),     // qty 0 counts
                row("فوطة", "أخرى", "متعدد الألوان", "وسط", null, null, "١٠٠", "٢٠", "١٢٠", "٨"),  // Arabic digits, consistent
                row("بطانية", "بطانيه", null, null, null, null, 50, 10, null, 1)                  // no colour, no size
        )));

        assertEquals(3, result.created());
        ArgumentCaptor<CreateProductRequest> products = ArgumentCaptor.forClass(CreateProductRequest.class);
        verify(productService, times(3)).create(products.capture());
        assertEquals("طقم 4", products.getAllValues().get(0).variants().get(0).size());
        assertEquals("متعدد الألوان", products.getAllValues().get(1).variants().get(0).color());
        assertEquals("أخرى", products.getAllValues().get(1).itemType());
        assertNull(products.getAllValues().get(2).variants().get(0).color());
        assertNull(products.getAllValues().get(2).variants().get(0).size());

        ArgumentCaptor<CreateProductPriceRequest> prices = ArgumentCaptor.forClass(CreateProductPriceRequest.class);
        verify(productService, times(3)).setPrice(anyLong(), prices.capture());
        for (CreateProductPriceRequest p : prices.getAllValues()) {
            assertTrue(p.profitPercentage() == null ^ p.sellingPrice() == null, "never both");
        }
        verify(stockService, times(2)).increaseStock(anyLong(), anyInt(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- drafts

    @Test
    void incompleteOrInvalidRowsAreDraftedWithBlankedValuesAndNotes() throws Exception {
        ProductImportResult result = service.importFile(xlsx(sheet(
                row("بدون سعر", "لحاف", "أبيض", "كينج", null, null, null, null, null, 5),          // 2: price missing
                row("لون غريب", "لحاف", "أزرق فاتح", "كينج", null, null, 100, 20, null, 5),       // 3: unknown colour -> must not become "no colour"
                row("كمية نص", "لحاف", "أبيض", "كينج", null, null, 100, 20, null, "كثير"),        // 4: quantity not a number
                row("سعر متعارض", "لحاف", "أبيض", "كينج", null, null, 100, 20, 999, 5),           // 5: profit/selling disagree
                row("x".repeat(250), "كرسي", "أبيض", "س".repeat(60), null, null, "abc", null, null, 1)  // 6: long name, bad type, size too long, bad cost
        )));

        assertEquals(0, result.created());
        assertEquals(5, result.drafted());
        verify(productService, never()).create(any());
        verifyNoInteractions(stockService);

        assertEquals(List.of("السعر"), result.drafts().get(0).missingFields());

        ProductDraft colour = savedDrafts.get(1);
        assertNull(colour.getVariants().get(0).getColor());
        assertTrue(colour.getImportNotes().contains("أزرق فاتح"));
        assertEquals(List.of("اللون"), result.drafts().get(1).missingFields(), "invalid colour counts as missing");

        assertNull(savedDrafts.get(2).getVariants().get(0).getQuantity());
        // No valid quantity -> the total is unknown too.
        assertEquals(List.of("الكمية الحالية", "إجمالي الكمية"), result.drafts().get(2).missingFields());

        ProductDraft conflict = savedDrafts.get(3);
        assertNull(conflict.getProfitPercentage());
        assertNull(conflict.getSellingPrice());
        assertNotNull(conflict.getPurchasePrice());

        assertTrue(result.drafts().get(4).missingFields().containsAll(List.of("الاسم", "النوع", "المقاس", "السعر")));
        assertNull(savedDrafts.get(4).getVariants().get(0).getSize(), "a size longer than the column is blanked");
    }

    @Test
    void rowMatchingAnExistingDraftIsSkipped() throws Exception {
        ProductDraft existingDraft = ProductDraft.builder().id(9L).name("بطانية").itemType("بطانيه").build();
        existingDraft.addVariant("بيج", "كبير", null, 2);
        when(draftRepository.findAll()).thenReturn(List.of(existingDraft));
        ProductImportResult result = service.importFile(xlsx(sheet(
                row("بطانية", "بطانيه", "بيج", "كبير", null, null, null, null, null, null))));
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("غير المكتملة"));
        assertTrue(savedDrafts.isEmpty());
    }

    @Test
    void incompleteRowsOfTheSameProductBecomeOneDraftWithSeveralColoursSizes() throws Exception {
        ProductImportResult result = service.importFile(xlsx(sheet(
                row("لحاف ناقص", "لحاف", "أحمر", "صغير", null, null, null, null, null, 4),
                row("لحاف ناقص", "لحاف", "أزرق فاتح", "وسط", null, null, null, null, null, 3),   // invalid colour
                row("لحاف ناقص", "لحاف", null, "كبير", null, null, 100, 20, null, null)          // price only here
        )));

        assertEquals(1, result.drafted());
        assertEquals(1, savedDrafts.size());
        ProductDraft d = savedDrafts.get(0);
        assertEquals(3, d.getVariants().size());
        assertEquals(List.of(2, 3, 4), d.getVariants().stream().map(v -> v.getSourceRow()).toList());
        assertEquals(0, new BigDecimal("100").compareTo(d.getPurchasePrice()), "price picked up from a later row");
        assertTrue(d.getImportNotes().contains("(الصف 3)"));
        // Still missing: one row has no quantity, and the cleared colour.
        assertTrue(result.drafts().get(0).missingFields().containsAll(List.of("الكمية الحالية", "اللون")));
    }

    // ---------------------------------------------------------------- file-level

    @Test
    void missingNameColumnRejectsButOtherMissingColumnsOnlyWarn() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("المنتجات");
            s.createRow(0).createCell(0).setCellValue("النوع");
            s.createRow(1).createCell(0).setCellValue("لحاف");
            wb.write(out);
            ProductImportValidationException ex = assertThrows(ProductImportValidationException.class,
                    () -> service.importFile(xlsx(out.toByteArray())));
            assertTrue(ex.getErrors().stream().anyMatch(e -> "الاسم".equals(e.column())));
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("المنتجات");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("الاسم *");
            h.createCell(1).setCellValue("النوع *");
            Row d = s.createRow(1);
            d.createCell(0).setCellValue("لحاف");
            d.createCell(1).setCellValue("لحاف");
            wb.write(out);
            ProductImportResult result = service.importFile(xlsx(out.toByteArray()));
            assertEquals(1, result.drafted());
            assertTrue(result.warnings().stream().anyMatch(w -> w.contains("سعر التكلفة")));
        }
    }

    @Test
    void blankRowsInTheMiddleAreSkipped() throws Exception {
        ProductImportResult result = service.importFile(xlsx(sheet(
                row("أ", "لحاف", "أبيض", "كينج", null, null, 100, 10, null, 1),
                row(null, null, null, null, null, null, null, null, null, null),
                row("ب", "لحاف", "أبيض", "كينج", null, null, 100, 10, null, 1)
        )));
        assertEquals(2, result.created());
    }

    @Test
    void wrongFileTypeAndGarbageAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.importFile(new MockMultipartFile("file", "p.csv", "text/csv", "a,b".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> service.importFile(xlsx("not an excel file".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> service.importFile(xlsx(new byte[0])));
    }

    @Test
    void moreThanMaxRowsIsRejectedBeforeAnythingIsSaved() throws Exception {
        Object[][] rows = new Object[ProductImportServiceImpl.MAX_ROWS + 1][];
        for (int i = 0; i < rows.length; i++) rows[i] = row("p" + i, "لحاف", "أبيض", "كينج", null, null, 100, 10, null, 1);
        assertThrows(ProductImportValidationException.class, () -> service.importFile(xlsx(sheet(rows))));
        verify(productService, never()).create(any());
        assertTrue(savedDrafts.isEmpty());
    }

    // ---------------------------------------------------------------- helpers

    private static Object[] row(Object... cells) {
        return cells;
    }

    private static byte[] sheet(Object[]... rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("المنتجات");
            Row h = s.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) h.createCell(i).setCellValue(HEADERS[i]);
            for (int r = 0; r < rows.length; r++) {
                Row row = s.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v instanceof Number n) row.createCell(c).setCellValue(n.doubleValue());
                    else if (v != null) row.createCell(c).setCellValue(v.toString());
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static MockMultipartFile xlsx(byte[] bytes) {
        return new MockMultipartFile("file", "p.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }
}
