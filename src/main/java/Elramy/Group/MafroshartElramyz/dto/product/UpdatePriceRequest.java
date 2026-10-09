package Elramy.Group.MafroshartElramyz.dto.product;

import java.math.BigDecimal;

public record UpdatePriceRequest(

        BigDecimal purchasePrice,

        BigDecimal profitPercentage,

        BigDecimal sellingPrice

) {
}