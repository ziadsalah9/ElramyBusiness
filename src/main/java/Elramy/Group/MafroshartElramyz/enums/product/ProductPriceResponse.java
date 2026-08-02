package Elramy.Group.MafroshartElramyz.enums.product;

import java.math.BigDecimal;

public record ProductPriceResponse(

        Long id,

        BigDecimal purchasePrice,

        BigDecimal sellingPrice,

        BigDecimal profitPercentage

) {}