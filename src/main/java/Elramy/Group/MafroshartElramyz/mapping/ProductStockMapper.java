package Elramy.Group.MafroshartElramyz.mapping;

import Elramy.Group.MafroshartElramyz.enums.stock.StockResponse;
import Elramy.Group.MafroshartElramyz.models.ProductStock;
import org.springframework.stereotype.Component;

@Component
public class ProductStockMapper {

    public StockResponse toResponse(ProductStock stock) {

        boolean lowStock =
                stock.getQuantity() <= stock.getMinimumQuantity();

        return new StockResponse(
                stock.getId(),

                stock.getProduct().getId(),
                stock.getProduct().getCode(),
                stock.getProduct().getName(),

                stock.getBranch().getId(),
                stock.getBranch().getName(),

                stock.getQuantity(),
                stock.getMinimumQuantity(),

                lowStock
        );
    }
}