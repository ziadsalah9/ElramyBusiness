package Elramy.Group.MafroshartElramyz.enums.product;

import java.math.BigDecimal;

public record UpdatePriceRequest(

        BigDecimal purchasePrice,

        BigDecimal profitPercentage,

        BigDecimal sellingPrice

) {
}