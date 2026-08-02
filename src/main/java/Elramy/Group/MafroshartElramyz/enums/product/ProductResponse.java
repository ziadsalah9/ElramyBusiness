package Elramy.Group.MafroshartElramyz.enums.product;

import java.math.BigDecimal;

public record ProductResponse(

        Long id,

        String code,

        String barcode,

        String name,

        String model,

        String itemType,

        String color,

        String size,


        ProductPriceResponse currentPrice,

        Integer minimumQuantity,

        Boolean active

) {
}