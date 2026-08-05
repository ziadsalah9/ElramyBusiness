package Elramy.Group.MafroshartElramyz.mapping;

import Elramy.Group.MafroshartElramyz.enums.product.ProductPriceResponse;
import Elramy.Group.MafroshartElramyz.enums.product.ProductResponse;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductPrice;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(
            Product product,
            ProductPrice currentPrice) {

        return new ProductResponse(

                product.getId(),

                product.getCode(),

                product.getBarcode(),

                product.getName(),

                product.getModel(),

                product.getItemType() != null
                        ? product.getItemType().name()
                        : null,

                product.getColor(),

                product.getSize(),

                toPriceResponse(currentPrice),

                product.getMinimumQuantity(),

                product.getActive()
        );
    }


    private ProductPriceResponse toPriceResponse(
            ProductPrice price) {

        if (price == null) {
            return null;
        }

        return new ProductPriceResponse(

                price.getId(),

                price.getPurchasePrice(),

                price.getSellingPrice(),

                price.getProfitPercentage(),

                price.getActive(),

                price.getCreatedAt()
        );
    }
}