package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.enums.product.CreateProductPriceRequest;
import Elramy.Group.MafroshartElramyz.enums.product.CreateProductRequest;
import Elramy.Group.MafroshartElramyz.enums.product.ProductDraftRequest;
import Elramy.Group.MafroshartElramyz.enums.product.ProductDraftResponse;
import Elramy.Group.MafroshartElramyz.enums.product.ProductDraftVariantDto;
import Elramy.Group.MafroshartElramyz.enums.product.ProductResponse;
import Elramy.Group.MafroshartElramyz.enums.product.ProductVariantResponse;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductDraft;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.repository.ProductDraftRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Drafts hold several colours/sizes with quantities (V12) and stay out of
 * `products` until complete; completing creates the product or adds the
 * colours/sizes to the matching one.
 */
class ProductDraftServiceImplTest {

    private ProductDraftRepository drafts;
    private ProductRepository products;
    private ProductService productService;
    private StockService stockService;
    private ProductDraftServiceImpl service;
    private ProductDraft draft;

    private static ProductVariantResponse variant(long id, String color, String size) {
        return new ProductVariantResponse(id, "PRD-55-" + id, "B" + id, color, size, color + " / " + size, 0, true);
    }

    @BeforeEach
    void setUp() {
        drafts = mock(ProductDraftRepository.class);
        products = mock(ProductRepository.class);
        productService = mock(ProductService.class);
        stockService = mock(StockService.class);
        service = new ProductDraftServiceImpl(drafts, products, productService, stockService, mock(Elramy.Group.MafroshartElramyz.activity.ActivityLogService.class));

        draft = ProductDraft.builder().id(7L).name("لحاف").itemType(ProductType.لحاف)
                .purchasePrice(new BigDecimal("100.00")).profitPercentage(new BigDecimal("20.00")).sourceRow(5).build();
        draft.addVariant("أحمر", "صغير", 4, 5);
        draft.addVariant("أصفر", "وسط", 3, 6);
        draft.addVariant(null, "كبير", 0, 7);
        draft.setTotalQuantity(7);
        when(drafts.findById(7L)).thenReturn(Optional.of(draft));
        when(drafts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(products.findAll()).thenReturn(List.of());
        List<ProductVariantResponse> created = List.of(variant(901, "أحمر", "صغير"), variant(902, "أصفر", "وسط"), variant(903, null, "كبير"));
        when(productService.create(any())).thenReturn(response(55L, created));
        when(productService.getById(anyLong())).thenAnswer(inv -> response(inv.getArgument(0), created));
    }

    private static ProductResponse response(long id, List<ProductVariantResponse> variants) {
        return new ProductResponse(id, "PRD-" + id, "لحاف", null, "لحاف", null, 0, true, 7, false, variants);
    }

    @Test
    void completingCreatesOneProductWithAllColoursSizesAndTheirOpeningStock() {
        ProductResponse created = service.complete(7L);

        assertEquals(55L, created.id());
        ArgumentCaptor<CreateProductRequest> req = ArgumentCaptor.forClass(CreateProductRequest.class);
        verify(productService).create(req.capture());
        assertEquals(3, req.getValue().variants().size());
        assertEquals("أحمر", req.getValue().variants().get(0).color());
        assertEquals(0, req.getValue().minimumQuantity(), "optional minimum defaults to 0");
        ArgumentCaptor<CreateProductPriceRequest> price = ArgumentCaptor.forClass(CreateProductPriceRequest.class);
        verify(productService).setPrice(eq(55L), price.capture());
        assertEquals(0, new BigDecimal("20.00").compareTo(price.getValue().profitPercentage()));

        verify(stockService).increaseStock(eq(901L), eq(4), eq(TransactionType.STOCK_IN), eq(new BigDecimal("100.00")), eq(7L), eq("PRODUCT_DRAFT"), anyString());
        verify(stockService).increaseStock(eq(902L), eq(3), eq(TransactionType.STOCK_IN), any(), eq(7L), eq("PRODUCT_DRAFT"), anyString());
        verify(stockService, never()).increaseStock(eq(903L), anyInt(), any(), any(), any(), any(), any()); // qty 0
        verify(drafts).delete(draft);
    }

    @Test
    void completingAddsTheColoursSizesToTheMatchingExistingProduct() {
        Product existing = Product.builder().id(3L).code("PRD-3").name(" لحاف ").itemType(ProductType.لحاف)
                .sellingPrice(new BigDecimal("130")).active(true).build();
        existing.getVariants().add(ProductVariant.builder().id(30L).product(existing).color("أبيض").code("PRD-3-1").build());
        when(products.findAll()).thenReturn(List.of(existing));
        when(productService.addVariant(eq(3L), any(), any())).thenAnswer(inv -> variant(800 + (long) (Math.random() * 99), inv.getArgument(1), inv.getArgument(2)));

        service.complete(7L);

        verify(productService, never()).create(any());
        verify(productService, times(3)).addVariant(eq(3L), any(), any());
        verify(productService, never()).setPrice(anyLong(), any()); // product already priced
        verify(stockService, times(2)).increaseStock(anyLong(), anyInt(), any(), any(), any(), any(), any());
        verify(drafts).delete(draft);
    }

    @Test
    void colourSizeAlreadyOnTheProductIsRejected() {
        Product existing = Product.builder().id(3L).code("PRD-3").name("لحاف").itemType(ProductType.لحاف).active(true).build();
        existing.getVariants().add(ProductVariant.builder().id(30L).product(existing).color("أصفر").size("وسط").code("PRD-3-1").build());
        when(products.findAll()).thenReturn(List.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.complete(7L));
        assertTrue(ex.getMessage().contains("PRD-3"));
        assertTrue(ex.getMessage().contains("أصفر / وسط"));
        verify(productService, never()).addVariant(anyLong(), any(), any());
        verify(drafts, never()).delete(any());
    }

    @Test
    void everyColourSizeNeedsAQuantity() {
        draft.getVariants().get(1).setQuantity(null);
        assertEquals(List.of("الكمية الحالية"), ProductDraftServiceImpl.toResponse(draft).missingFields());
        assertThrows(IllegalArgumentException.class, () -> service.complete(7L));
        verify(productService, never()).create(any());
    }

    @Test
    void updateReplacesTheColoursSizesKeepsIdsAndValidates() {
        Long redId = 1L;
        draft.getVariants().get(0).setId(redId);
        ProductDraftResponse res = service.update(7L, new ProductDraftRequest(
                "لحاف", "لحاف", null, 2, new BigDecimal("100"), new BigDecimal("20"), null,
                List.of(new ProductDraftVariantDto(redId, "أحمر", "صغير", 10, null),
                        new ProductDraftVariantDto(null, "متعدد الألوان", "طقم 4 قطع", 5, null)), 15));

        assertEquals(2, draft.getVariants().size(), "rows left out are removed");
        assertEquals(10, draft.getVariants().get(0).getQuantity());
        assertEquals("متعدد", draft.getVariants().get(1).getColor(), "labels resolved");
        assertEquals("طقم 4", draft.getVariants().get(1).getSize());
        assertTrue(res.missingFields().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> service.update(7L, new ProductDraftRequest(
                "x", null, null, null, null, null, null,
                List.of(new ProductDraftVariantDto(null, "أحمر", null, 1, null), new ProductDraftVariantDto(null, " أحمر ", null, 2, null)))));
        assertThrows(IllegalArgumentException.class, () -> service.update(7L, new ProductDraftRequest(
                "x", null, null, null, null, null, null, List.of(new ProductDraftVariantDto(null, "أزرق فاتح", null, 1, null)))));
        assertThrows(IllegalArgumentException.class, () -> service.update(7L, new ProductDraftRequest(
                "x", null, null, null, null, null, null, List.of(new ProductDraftVariantDto(null, null, null, -1, null)))));
        assertThrows(IllegalArgumentException.class, () -> service.update(7L, new ProductDraftRequest(
                "x", null, null, null, null, null, null, List.of())));
    }

    @Test
    void invalidImportedColourCountsAsMissingUntilTheFormIsSaved() {
        draft.getVariants().get(0).setColor(null);
        draft.setImportNotes("اللون: \"أزرق فاتح\" غير موجود في القائمة، تُرك فارغاً (الصف 5)");
        assertEquals(List.of("اللون"), ProductDraftServiceImpl.toResponse(draft).missingFields());

        ProductDraftResponse res = service.update(7L, new ProductDraftRequest(
                "لحاف", "لحاف", null, null, new BigDecimal("100"), new BigDecimal("20"), null,
                List.of(new ProductDraftVariantDto(null, null, "صغير", 4, 5)), 4));
        assertTrue(res.missingFields().isEmpty(), "saving the form = 'no colour' is deliberate");
        assertTrue(res.notes().isEmpty());
    }

    @Test
    void unknownDraftIs404() {
        when(drafts.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.complete(99L));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(99L));
    }

    @Test
    void theTotalIsRequiredAndColoursSizesMayNotExceedIt() {
        draft.setTotalQuantity(null);
        assertTrue(ProductDraftServiceImpl.toResponse(draft).missingFields().contains("إجمالي الكمية"));
        assertThrows(IllegalArgumentException.class, () -> service.complete(7L));

        IllegalArgumentException over = assertThrows(IllegalArgumentException.class, () -> service.update(7L, new ProductDraftRequest(
                "لحاف", "لحاف", null, null, new BigDecimal("100"), new BigDecimal("20"), null,
                List.of(new ProductDraftVariantDto(null, "أحمر", "صغير", 6, null)), 5)));
        assertTrue(over.getMessage().contains("أكبر من"));
    }

    @Test
    void completingKeepsWhatIsLeftOfTheTotalAsNoColourOrSize() {
        draft.setTotalQuantity(10);   // colours/sizes: 4 + 3 + 0 ("كبير" has no colour, but a size)
        List<ProductVariantResponse> created = List.of(variant(901, "أحمر", "صغير"), variant(902, "أصفر", "وسط"),
                variant(903, null, "كبير"), variant(904, null, null));
        when(productService.create(any())).thenReturn(response(55L, created));

        service.complete(7L);

        ArgumentCaptor<CreateProductRequest> req = ArgumentCaptor.forClass(CreateProductRequest.class);
        verify(productService).create(req.capture());
        assertEquals(4, req.getValue().variants().size(), "a no-colour/no-size row is added for the rest");
        verify(stockService).increaseStock(eq(904L), eq(3), eq(TransactionType.STOCK_IN), any(), eq(7L), eq("PRODUCT_DRAFT"), anyString());
    }
}
