package Elramy.Group.MafroshartElramyz.mapping;

import Elramy.Group.MafroshartElramyz.enums.product.ProductPriceResponse;
import Elramy.Group.MafroshartElramyz.enums.product.ProductResponse;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductPrice;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product, ProductPrice price) {

        ProductPriceResponse currentPrice = null;

        if (price != null) {
            currentPrice = new ProductPriceResponse(
                    price.getId(),
                    price.getPurchasePrice(),
                    price.getSellingPrice(),
                    price.getProfitPercentage()
            );
        }

        return new ProductResponse(
                product.getId(),
                product.getCode(),
                product.getBarcode(),
                product.getName(),
                product.getModel(),
                product.getItemType().name(),
                product.getColor(),
                product.getSize(),
              currentPrice,
                product.getMinimumQuantity(),
                product.getActive()
        );
    }

}