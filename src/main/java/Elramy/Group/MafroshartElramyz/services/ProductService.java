package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.product.CreateProductRequest;
import Elramy.Group.MafroshartElramyz.enums.product.ProductResponse;
import Elramy.Group.MafroshartElramyz.enums.product.UpdatePriceRequest;
import Elramy.Group.MafroshartElramyz.enums.product.UpdateProductRequest;

import java.util.List;

public interface ProductService {

    ProductResponse create(CreateProductRequest request);


    ProductResponse update(Long id, UpdateProductRequest request);

    ProductResponse getById(Long id);

    List<ProductResponse> getAll();

    void toggleStatus(Long id);

    ProductResponse updatePrice(Long id, UpdatePriceRequest request);

    List<ProductResponse> search(String keyword);

}