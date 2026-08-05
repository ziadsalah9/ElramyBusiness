package Elramy.Group.MafroshartElramyz.enums.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductPriceResponse(

        Long id,

        BigDecimal purchasePrice,

        BigDecimal sellingPrice,

        BigDecimal profitPercentage,


        Boolean active,

        LocalDateTime createdAt

) {}