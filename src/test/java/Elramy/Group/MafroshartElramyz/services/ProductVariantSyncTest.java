package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.dto.product.CreateProductRequest;
import Elramy.Group.MafroshartElramyz.dto.product.ProductResponse;
import Elramy.Group.MafroshartElramyz.dto.product.ProductVariantRequest;
import Elramy.Group.MafroshartElramyz.dto.product.UpdateProductRequest;
import Elramy.Group.MafroshartElramyz.mapping.ProductMapper;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import Elramy.Group.MafroshartElramyz.repository.StockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Colour/size variants of a product: create, add, update, remove. */
class ProductVariantSyncTest {

    private ProductRepository products;
    private ProductVariantRepository variants;
    private StockTransactionRepository ledger;
    private StockService stock;
    private ProductServiceImpl service;
    private Product saved;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        variants = mock(ProductVariantRepository.class);
        ledger = mock(StockTransactionRepository.class);
        stock = mock(StockService.class);
        service = new ProductServiceImpl(products, variants, ledger, stock, new ProductMapper(), mock(Elramy.Group.MafroshartElramyz.activity.ActivityLogService.class), Elramy.Group.MafroshartElramyz.producttype.TestItemTypes.mock(), Elramy.Group.MafroshartElramyz.productcolor.TestColors.mock());

        when(products.existsByCode(anyString())).thenReturn(false);
        when(variants.existsByCode(anyString())).thenReturn(false);
        when(products.save(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            saved = p;
            return p;
        });
        AtomicLong ids = new AtomicLong(10);
        when(variants.save(any())).thenAnswer(inv -> {
            ProductVariant v = inv.getArgument(0);
            if (v.getId() == null) v.setId(ids.incrementAndGet());
            return v;
        });
        when(products.findById(1L)).thenAnswer(inv -> Optional.ofNullable(saved));
    }

    private static ProductVariantRequest v(Long id, String color, String size) {
        return new ProductVariantRequest(id, color, size);
    }

    private ProductResponse create(ProductVariantRequest... vs) {
        return service.create(new CreateProductRequest("لحاف", null, "لحاف", 5, Arrays.asList(vs)));
    }

    @Test
    void productIsCreatedWithOneVariantPerColourSizeEachWithOwnCodeAndBarcode() {
        ProductResponse p = create(v(null, "أحمر", "صغير"), v(null, "أصفر", "وسط"), v(null, null, "كبير"));

        assertEquals(3, p.variants().size());
        assertEquals(List.of("أحمر / صغير", "أصفر / وسط", "كبير"), p.variants().stream().map(x -> x.label()).toList());
        assertEquals(3, p.variants().stream().map(x -> x.code()).distinct().count());
        assertTrue(p.variants().get(0).code().startsWith(saved.getCode() + "-"));
        assertTrue(p.variants().stream().allMatch(x -> x.barcode() != null && x.quantity() == 0));
        // Short barcodes: 100000 + id, unique like the ids (saved variants here get ids 11, 12, 13).
        assertEquals(List.of("100011", "100012", "100013"), p.variants().stream().map(x -> x.barcode()).toList());
        assertEquals(0, p.quantity());
    }

    @Test
    void noColoursOrSizesGivesOneDefaultVariant() {
        ProductResponse p = service.create(new CreateProductRequest("فوطة", null, "فوط", 0, null));
        assertEquals(1, p.variants().size());
        assertEquals("بدون لون أو مقاس", p.variants().get(0).label());
    }

    @Test
    void uiLabelsAreResolvedAndUnknownValuesRejected() {
        ProductResponse p = create(v(null, "متعدد الألوان", "طقم 4 قطع"));
        assertEquals("متعدد الألوان", p.variants().get(0).color());
        assertEquals("طقم 4", p.variants().get(0).size());

        assertThrows(IllegalArgumentException.class, () -> create(v(null, "أزرق فاتح", null)));
    }

    @Test
    void theRestOfTheTotalBecomesNoColourOrSize() {
        ProductResponse p = service.create(new CreateProductRequest("لحاف", null, "لحاف", 5,
                List.of(new ProductVariantRequest(null, "أحمر", "صغير", 4), new ProductVariantRequest(null, "أصفر", null, 3)), 10));

        assertEquals(List.of("أحمر / صغير", "أصفر", "بدون لون أو مقاس"), p.variants().stream().map(x -> x.label()).toList());
        verify(stock).increaseStock(eq(p.variants().get(2).id()), eq(3), any(), any(), any(), any(), any());
    }

    @Test
    void coloursSizesAddingUpToMoreThanTheTotalAreRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(new CreateProductRequest(
                "لحاف", null, "لحاف", 5, List.of(new ProductVariantRequest(null, "أحمر", null, 8)), 5)));
        assertTrue(ex.getMessage().contains("أكبر من"));
    }

    @Test
    void withATotalAStaleUnchangedRowIsRejectedToo() {
        create(v(null, "أحمر", null), v(null, "أصفر", null));
        ProductVariant red = saved.getVariants().get(0);
        ProductVariant yellow = saved.getVariants().get(1);
        red.setQuantity(5);
        yellow.setQuantity(2);   // the form showed 4: one was sold meanwhile

        assertThrows(Elramy.Group.MafroshartElramyz.exception.ConflictException.class, () ->
                service.update(1L, new UpdateProductRequest("لحاف", null, "لحاف", 5, List.of(
                        new ProductVariantRequest(red.getId(), "أحمر", null, 6, 5),
                        new ProductVariantRequest(yellow.getId(), "أصفر", null, null, 3)), 9)));
    }

    @Test
    void sizeIsFreeTextTrimmedAndLimitedToTheColumn() {
        ProductResponse p = create(v(null, "أبيض", "  200x230   سم "), v(null, "أبيض", "عملاق"));
        assertEquals(List.of("200x230 سم", "عملاق"), p.variants().stream().map(x -> x.size()).toList());

        assertThrows(IllegalArgumentException.class, () -> create(v(null, null, "س".repeat(51))));
        assertThrows(IllegalArgumentException.class, () -> create(v(null, "أبيض", "كبير"), v(null, "أبيض", " كبير ")));
    }

    @Test
    void duplicateColourSizeInOneRequestIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> create(v(null, "أحمر", "صغير"), v(null, " أحمر ", "صغير")));
    }

    @Test
    void updateAddsKeepsAndRemovesVariants() {
        create(v(null, "أحمر", "صغير"), v(null, "أصفر", "وسط"), v(null, "أبيض", "كبير"));
        ProductVariant red = saved.getVariants().get(0);
        ProductVariant yellow = saved.getVariants().get(1);
        ProductVariant white = saved.getVariants().get(2);
        yellow.setQuantity(0);
        when(ledger.existsByVariantId(yellow.getId())).thenReturn(true);   // sold before -> keep history
        when(ledger.existsByVariantId(white.getId())).thenReturn(false);   // never used -> delete

        ProductResponse p = service.update(1L, new UpdateProductRequest("لحاف", null, "لحاف", 5,
                List.of(v(red.getId(), "أحمر", "صغير"), v(null, "أسود", "مزدوج"))));

        assertEquals(List.of("أحمر / صغير", "أسود / مزدوج"), p.variants().stream().map(x -> x.label()).toList());
        assertFalse(yellow.getActive(), "used variant is deactivated, not deleted");
        verify(variants).delete(white);
        verify(variants, never()).delete(yellow);
    }

    @Test
    void variantWithStockCannotBeRemoved() {
        create(v(null, "أحمر", "صغير"), v(null, "أصفر", "وسط"));
        ProductVariant red = saved.getVariants().get(0);
        saved.getVariants().get(1).setQuantity(3);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.update(1L,
                new UpdateProductRequest("لحاف", null, "لحاف", 5, List.of(v(red.getId(), "أحمر", "صغير")))));
        assertTrue(ex.getMessage().contains("مخزون"));
    }

    @Test
    void productMustKeepAtLeastOneVariant() {
        create(v(null, "أحمر", "صغير"));
        assertThrows(IllegalArgumentException.class, () -> service.update(1L,
                new UpdateProductRequest("لحاف", null, "لحاف", 5, List.of())));
    }

    @Test
    void addingAnExistingInactiveColourSizeReactivatesIt() {
        create(v(null, "أحمر", "صغير"), v(null, "أصفر", "وسط"));
        ProductVariant yellow = saved.getVariants().get(1);
        yellow.setActive(false);

        var added = service.addVariant(1L, "أصفر", "وسط");

        assertEquals(yellow.getId(), added.id());
        assertTrue(yellow.getActive());
        assertEquals(2, saved.getVariants().size(), "no duplicate row");
    }

    @Test
    void lookupFindsByCodeBarcodeOrAnOldPrintedBarcode() {
        create(v(null, "أحمر", "صغير"));
        ProductVariant red = saved.getVariants().get(0);
        when(variants.findByScanCode(anyString())).thenCallRealMethod();   // code -> barcode -> legacy barcode
        when(variants.findByCode(anyString())).thenReturn(Optional.empty());
        when(variants.findByBarcode(anyString())).thenReturn(Optional.empty());
        when(variants.findByLegacyBarcode(anyString())).thenReturn(Optional.empty());
        when(variants.findByBarcode("100011")).thenReturn(Optional.of(red));
        when(variants.findByLegacyBarcode("200793466870")).thenReturn(Optional.of(red));

        var found = service.lookupVariant(" 100011 ");
        assertEquals(red.getId(), found.variant().id());
        assertEquals(red.getId(), service.lookupVariant("200793466870").variant().id(), "labels printed before V23 still scan");
        assertEquals("لحاف", found.product().name());
        verify(ledger, never()).existsByVariantId(anyLong());
    }

    // ---------------------------------------------------------------- quantity typed in the product form

    @Test
    void quantityForANewColourSizeIsRecordedAsOpeningStock() {
        service.create(new CreateProductRequest("لحاف", null, "لحاف", 5, List.of(
                new ProductVariantRequest(null, null, "كبير", 3),
                new ProductVariantRequest(null, "أحمر", "صغير", 4),
                new ProductVariantRequest(null, "أصفر", "وسط", 0))));   // 0 = nothing to record

        Long large = saved.getVariants().get(0).getId();
        Long redSmall = saved.getVariants().get(1).getId();
        verify(stock).increaseStock(eq(large), eq(3), eq(TransactionType.STOCK_IN), isNull(), eq(1L), eq("PRODUCT_FORM"), anyString());
        verify(stock).increaseStock(eq(redSmall), eq(4), eq(TransactionType.STOCK_IN), isNull(), eq(1L), eq("PRODUCT_FORM"), anyString());
        verify(stock, times(2)).increaseStock(anyLong(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void changingAnExistingQuantityIsRecordedAsAnAdjustment() {
        create(v(null, "أحمر", "صغير"), v(null, "أصفر", "وسط"), v(null, "أبيض", "كبير"));
        ProductVariant red = saved.getVariants().get(0);
        ProductVariant yellow = saved.getVariants().get(1);
        ProductVariant white = saved.getVariants().get(2);
        red.setQuantity(4);
        yellow.setQuantity(5);
        white.setQuantity(2);

        service.update(1L, new UpdateProductRequest("لحاف", null, "لحاف", 5, List.of(
                new ProductVariantRequest(red.getId(), "أحمر", "صغير", 10),      // +6
                new ProductVariantRequest(yellow.getId(), "أصفر", "وسط", 2),     // -3
                new ProductVariantRequest(white.getId(), "أبيض", "كبير", null)))); // unchanged

        verify(stock).increaseStock(eq(red.getId()), eq(6), eq(TransactionType.ADJUSTMENT_IN), isNull(), eq(1L), eq("PRODUCT_FORM"), anyString());
        verify(stock).decreaseStock(eq(yellow.getId()), eq(3), eq(TransactionType.ADJUSTMENT_OUT), isNull(), eq(1L), eq("PRODUCT_FORM"), anyString());
        verify(stock, never()).increaseStock(eq(white.getId()), anyInt(), any(), any(), any(), any(), any());
        verify(stock, never()).decreaseStock(eq(white.getId()), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void negativeQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateProductRequest("لحاف", null, "لحاف", 0,
                List.of(new ProductVariantRequest(null, "أحمر", null, -1)))));
        verifyNoInteractions(stock);
    }

    @Test
    void aQuantityEditBasedOnStaleStockIsRejectedInsteadOfUndoingASale() {
        create(v(null, "أحمر", "صغير"));
        ProductVariant red = saved.getVariants().get(0);
        red.setQuantity(7);   // the form opened at 10; a sale of 3 happened since

        assertThrows(Elramy.Group.MafroshartElramyz.exception.ConflictException.class, () ->
                service.update(1L, new UpdateProductRequest("لحاف", null, "لحاف", 5,
                        List.of(new ProductVariantRequest(red.getId(), "أحمر", "صغير", 12, 10)))));
        verify(stock, never()).increaseStock(anyLong(), anyInt(), any(), any(), any(), any(), any());

        // Same edit from up-to-date stock goes through as +5.
        service.update(1L, new UpdateProductRequest("لحاف", null, "لحاف", 5,
                List.of(new ProductVariantRequest(red.getId(), "أحمر", "صغير", 12, 7))));
        verify(stock).increaseStock(eq(red.getId()), eq(5), any(), any(), any(), any(), any());
    }

    @Test
    void anUntouchedQuantityLeavesTheStockAlone() {
        create(v(null, "أحمر", "صغير"));
        ProductVariant red = saved.getVariants().get(0);
        red.setQuantity(7);

        service.update(1L, new UpdateProductRequest("لحاف تعديل", null, "لحاف", 5,
                List.of(new ProductVariantRequest(red.getId(), "أحمر", "صغير", null, null))));
        verifyNoInteractions(stock);
    }
}
