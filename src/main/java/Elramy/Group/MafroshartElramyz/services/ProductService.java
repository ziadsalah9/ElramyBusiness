package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.product.*;

import java.util.List;

public interface ProductService {

    ProductResponse create(CreateProductRequest request);


    ProductResponse update(Long id, UpdateProductRequest request);

    ProductResponse getById(Long id);

    ProductPriceResponse getPrice(Long productId);

    void delete(Long id);

    List<ProductResponse> getAll();

    void toggleStatus(Long id);


    /** Sets (first time or changes) the product's current price. */
    ProductResponse setPrice(
            Long productId,
            CreateProductPriceRequest request
    );

    List<ProductResponse> search(String keyword);

    /** Active products at or below their minimum quantity. */
    List<ProductResponse> getLowStock();

    /** Finds a variant by its code or barcode (for scanning on invoices). */
    VariantLookupResponse lookupVariant(String code);

    /**
     * Adds one colour/size to an existing product, or returns it when the
     * product already has that combination (reactivating it if needed).
     */
    ProductVariantResponse addVariant(Long productId, String color, String size);

}