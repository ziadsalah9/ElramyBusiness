package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.ProductType;
import Elramy.Group.MafroshartElramyz.enums.product.CreateProductPriceRequest;
import Elramy.Group.MafroshartElramyz.enums.product.ProductResponse;
import Elramy.Group.MafroshartElramyz.exception.PriceNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.ProductNotFoundException;
import Elramy.Group.MafroshartElramyz.mapping.ProductMapper;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import Elramy.Group.MafroshartElramyz.repository.StockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The current price lives on Product (no separate price table). */
class ProductServiceImplPriceTest {

    private ProductRepository productRepository;
    private ProductServiceImpl service;
    private Product product;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        service = new ProductServiceImpl(productRepository, mock(ProductVariantRepository.class), mock(StockTransactionRepository.class), mock(StockService.class), new ProductMapper(), mock(Elramy.Group.MafroshartElramyz.activity.ActivityLogService.class));
        product = Product.builder().id(7L).code("PRD-7").name("لحاف").itemType(ProductType.لحاف)
                .active(true).build();
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void firstPriceFromProfitIsStoredOnProduct() {
        ProductResponse res = service.setPrice(7L, new CreateProductPriceRequest(bd("150"), bd("30"), null));

        assertEquals(0, bd("195.00").compareTo(product.getSellingPrice()));
        assertEquals(0, bd("30").compareTo(product.getProfitPercentage()));
        assertNotNull(product.getPriceUpdatedAt());
        assertEquals(0, bd("195.00").compareTo(res.currentPrice().sellingPrice()));
        assertEquals(0, res.quantity(), "new product starts with no stock");
        verify(productRepository).save(product);
    }

    @Test
    void changingAnExistingPriceJustOverwritesIt() {
        service.setPrice(7L, new CreateProductPriceRequest(bd("150"), bd("30"), null));
        // Previously this failed with "already has a price" (POST) or needed PATCH.
        service.setPrice(7L, new CreateProductPriceRequest(bd("200"), null, bd("250")));

        assertEquals(0, bd("250.00").compareTo(product.getSellingPrice()));
        assertEquals(0, bd("200.00").compareTo(product.getPurchasePrice()));
        assertEquals(0, bd("25.00").compareTo(product.getProfitPercentage()));
        verify(productRepository, times(2)).save(product);
    }

    @Test
    void roundingIsHalfUpToTwoDecimals() {
        // Cost is rounded to 33.33 first; 33.33 + 10% = 36.663 -> 36.66.
        service.setPrice(7L, new CreateProductPriceRequest(bd("33.333"), bd("10"), null));
        assertEquals(0, bd("36.66").compareTo(product.getSellingPrice()));
        assertEquals(0, bd("33.33").compareTo(product.getPurchasePrice()));
    }

    @Test
    void sellingBelowCostIsAllowedAndGivesNegativeProfit() {
        service.setPrice(7L, new CreateProductPriceRequest(bd("100"), null, bd("80")));
        assertEquals(0, bd("-20.00").compareTo(product.getProfitPercentage()));
    }

    @Test
    void invalidRequestsAreRejectedAndNothingIsWritten() {
        assertThrows(IllegalArgumentException.class, () -> service.setPrice(7L, new CreateProductPriceRequest(null, bd("10"), null)));
        assertThrows(IllegalArgumentException.class, () -> service.setPrice(7L, new CreateProductPriceRequest(bd("0"), bd("10"), null)));
        assertThrows(IllegalArgumentException.class, () -> service.setPrice(7L, new CreateProductPriceRequest(bd("100"), bd("10"), bd("110"))));
        assertThrows(IllegalArgumentException.class, () -> service.setPrice(7L, new CreateProductPriceRequest(bd("100"), null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.setPrice(7L, new CreateProductPriceRequest(bd("100"), bd("-1"), null)));
        assertThrows(IllegalArgumentException.class, () -> service.setPrice(7L, new CreateProductPriceRequest(bd("100"), null, bd("0"))));
        assertNull(product.getSellingPrice());
        verify(productRepository, never()).save(any());
    }

    @Test
    void unknownProductIs404() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ProductNotFoundException.class,
                () -> service.setPrice(99L, new CreateProductPriceRequest(bd("100"), bd("10"), null)));
    }

    @Test
    void getPriceReadsTheProductAndIs404WhenUnpriced() {
        assertThrows(PriceNotFoundException.class, () -> service.getPrice(7L));
        service.setPrice(7L, new CreateProductPriceRequest(bd("1000"), bd("10"), null));
        assertEquals(0, bd("1100.00").compareTo(service.getPrice(7L).sellingPrice()));
    }
}
