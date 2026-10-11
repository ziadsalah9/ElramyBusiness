package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.dto.product.ProductDraftRequest;
import Elramy.Group.MafroshartElramyz.dto.product.ProductDraftResponse;
import Elramy.Group.MafroshartElramyz.dto.product.ProductResponse;

import java.util.List;

/** Incomplete products ("منتجات غير مكتملة") waiting to be completed. */
public interface ProductDraftService {

    List<ProductDraftResponse> list();

    long count();

    ProductDraftResponse update(Long id, ProductDraftRequest request);

    /**
     * Creates the real product (with price and opening stock) and deletes
     * the draft. Fails with 400 when a required field is still missing or
     * an identical product already exists.
     */
    ProductResponse complete(Long id);

    void delete(Long id);
}
