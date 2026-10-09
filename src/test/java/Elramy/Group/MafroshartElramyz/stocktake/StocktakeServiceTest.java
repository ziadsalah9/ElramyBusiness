package Elramy.Group.MafroshartElramyz.stocktake;

import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.enums.ProductType;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.exception.ConflictException;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import Elramy.Group.MafroshartElramyz.services.StockService;
import Elramy.Group.MafroshartElramyz.services.UserService;
import Elramy.Group.MafroshartElramyz.stocktake.StocktakeDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Blind counting with a per-line snapshot, and approval that applies the
 * difference to the current stock (sales during the count are kept).
 */
class StocktakeServiceTest {

    private StocktakeRepository stocktakes;
    private ProductVariantRepository variants;
    private StockService stock;
    private EntityManager em;
    private StocktakeService service;

    private Stocktake open;
    private ProductVariant red;      // لحاف أحمر, system 10
    private ProductVariant blue;     // لحاف أزرق, system 5
    private ProductVariant towel;    // فوط, out of a لحاف-only scope

    @BeforeEach
    void setUp() {
        stocktakes = mock(StocktakeRepository.class);
        variants = mock(ProductVariantRepository.class);
        stock = mock(StockService.class);
        em = mock(EntityManager.class);
        UserService users = mock(UserService.class);
        User admin = new User();
        admin.setId(1L);
        admin.setFullName("المدير");
        when(users.getCurrentUser()).thenReturn(admin);
        service = new StocktakeService(stocktakes, variants, stock, users, mock(ActivityLogService.class), em);

        Product quilt = Product.builder().id(1L).name("لحاف").itemType(ProductType.لحاف)
                .purchasePrice(new BigDecimal("100")).sellingPrice(new BigDecimal("150")).active(true).build();
        Product towels = Product.builder().id(2L).name("فوطة").itemType(ProductType.فوط).active(true).build();
        red = variant(11L, quilt, "أحمر", 10);
        blue = variant(12L, quilt, "أزرق", 5);
        towel = variant(21L, towels, "أبيض", 3);

        open = Stocktake.builder().id(7L).code("STK-1").title("جرد اللحاف").status(StocktakeStatus.OPEN).build();
        open.setScope(List.of(ProductType.لحاف));
        when(stocktakes.findById(7L)).thenReturn(Optional.of(open));
        when(stocktakes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variants.findCountableByTypes(any())).thenReturn(List.of(red, blue));
        for (ProductVariant v : List.of(red, blue, towel)) {
            when(variants.findById(v.getId())).thenReturn(Optional.of(v));
            when(variants.findByCode(v.getCode())).thenReturn(Optional.of(v));
            when(em.find(ProductVariant.class, v.getId())).thenReturn(v);
        }
    }

    private static ProductVariant variant(long id, Product p, String color, int qty) {
        ProductVariant v = ProductVariant.builder().id(id).product(p).color(color).code("C-" + id).barcode("B" + id)
                .quantity(qty).active(true).build();
        p.getVariants().add(v);
        return v;
    }

    @Test
    void onlyOneStocktakeCanBeOpen() {
        when(stocktakes.findFirstByStatus(StocktakeStatus.OPEN)).thenReturn(Optional.of(open));
        assertThrows(ConflictException.class, () -> service.create(new CreateRequest("جرد", null, null)));
    }

    @Test
    void countingStoresTheSystemQuantityAtThatMomentAndRecountRefreshesIt() {
        service.setCount(7L, 11L, 8);
        assertEquals(10, open.getLines().get(0).getSystemQtyAtCount());

        red.setQuantity(9);            // a sale happens, then the shelf is recounted
        service.setCount(7L, 11L, 7);
        assertEquals(1, open.getLines().size());
        assertEquals(9, open.getLines().get(0).getSystemQtyAtCount());
        assertEquals(-2, open.getLines().get(0).variance());
    }

    @Test
    void scanAddsOnePieceAndTheCountingViewIsBlind() {
        when(variants.findByBarcode("B11")).thenReturn(Optional.of(red));
        assertTrue(service.scan(7L, " B11 ").created());      // by barcode
        Scanned second = service.scan(7L, "C-11");            // by code

        assertEquals(2, second.line().countedQty());
        assertFalse(second.created());
        // CountLine has no system quantity field at all.
        assertEquals(0, java.util.Arrays.stream(CountLine.class.getRecordComponents())
                .filter(c -> c.getName().toLowerCase().contains("system")).count());
    }

    @Test
    void outOfScopeOrInactiveColoursAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.setCount(7L, 21L, 1));
        blue.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.setCount(7L, 12L, 1));
        assertTrue(open.getLines().isEmpty());
    }

    @Test
    void approvalAppliesTheDifferenceToCurrentStockSoSalesDuringTheCountAreKept() {
        service.setCount(7L, 11L, 8);   // counted 8 while the system had 10 -> shortage 2
        service.setCount(7L, 12L, 6);   // counted 6 while the system had 5  -> surplus 1
        red.setQuantity(7);             // 3 sold after counting

        Review r = service.approve(7L);

        // Order: variant 11 before 12 (deadlock-safe); difference, not "set to 8".
        InOrder order = inOrder(stock);
        order.verify(stock).decreaseStock(eq(11L), eq(2), eq(TransactionType.ADJUSTMENT_OUT), any(), eq(7L), eq("STOCKTAKE"), anyString());
        order.verify(stock).increaseStock(eq(12L), eq(1), eq(TransactionType.ADJUSTMENT_IN), any(), eq(7L), eq("STOCKTAKE"), anyString());
        verify(em).refresh(red, LockModeType.PESSIMISTIC_WRITE);

        assertEquals(StocktakeStatus.APPROVED, open.getStatus());
        assertEquals(0, new BigDecimal("200").compareTo(r.shortageCost()));   // 2 x 100
        assertEquals(0, new BigDecimal("100").compareTo(r.surplusCost()));    // 1 x 100
        assertEquals(0, new BigDecimal("300").compareTo(r.shortageSell()));   // 2 x 150
    }

    @Test
    void aShortageLargerThanWhatIsLeftStopsAtZeroAndIsFlagged() {
        service.setCount(7L, 11L, 4);   // shortage 6 against 10
        red.setQuantity(2);             // 8 sold since: only 2 left

        service.approve(7L);

        verify(stock).decreaseStock(eq(11L), eq(2), any(), any(), any(), any(), any());
        StocktakeLine line = open.getLines().get(0);
        assertTrue(line.getClamped());
        assertEquals(-2, line.getAppliedVariance());
    }

    @Test
    void matchingCountsAndUncountedItemsLeaveStockUntouched() {
        service.setCount(7L, 11L, 10);  // blue is never counted

        Review before = service.getReview(7L);
        assertEquals(List.of(12L), before.uncounted().stream().map(Uncounted::variantId).toList());

        service.approve(7L);
        verifyNoInteractions(stock);
    }

    @Test
    void anApprovedStocktakeCannotBeChanged() {
        service.setCount(7L, 11L, 10);
        service.approve(7L);
        assertThrows(ConflictException.class, () -> service.setCount(7L, 11L, 3));
        assertThrows(ConflictException.class, () -> service.cancel(7L));
        assertThrows(ConflictException.class, () -> service.approve(7L));
    }

    @Test
    void approvingWithNothingCountedIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.approve(7L));
    }

    @Test
    void countSheetRoundTripFillsCountsAndReportsBadRows() throws Exception {
        byte[] sheet = service.buildSheet(7L);
        byte[] filled;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(sheet)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(StocktakeService.H_COUNT, s.getRow(0).getCell(6).getStringCellValue());
            assertEquals("C-11", s.getRow(1).getCell(0).getStringCellValue());
            s.getRow(1).getCell(6).setCellValue(9);                    // red
            s.getRow(2).getCell(6).setCellValue("ثلاثة");               // blue: invalid
            var extra = s.createRow(3);
            extra.createCell(0).setCellValue("C-21");                   // towel: out of scope
            extra.createCell(6).setCellValue(1);
            var ghost = s.createRow(4);
            ghost.createCell(0).setCellValue("NOPE");                   // unknown code
            ghost.createCell(6).setCellValue(1);
            wb.write(out);
            filled = out.toByteArray();
        }

        ImportResult r = service.importCounts(7L, new MockMultipartFile("file", "count.xlsx", null, filled));

        assertEquals(1, r.filled());
        assertEquals(1, r.invalid().size());
        assertEquals(1, r.outOfScope().size());
        assertEquals(1, r.unknown().size());
        assertEquals(9, open.getLines().get(0).getCountedQty());
    }

    @Test
    void countsMustBeWholeNonNegativeNumbers() {
        assertEquals(5, StocktakeService.parseCount("5.0"));
        assertNull(StocktakeService.parseCount("2.5"));
        assertNull(StocktakeService.parseCount("-1"));
        assertNull(StocktakeService.parseCount("abc"));
    }
}
