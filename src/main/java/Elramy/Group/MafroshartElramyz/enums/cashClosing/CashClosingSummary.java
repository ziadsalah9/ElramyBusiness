package Elramy.Group.MafroshartElramyz.enums.cashClosing;

import java.math.BigDecimal;

public record CashClosingSummary(

        Long branchId,
        String branchName,

        BigDecimal cashSalesTotal,
        BigDecimal countedTotal,
        BigDecimal difference,

        Integer count5,
        Integer count10,
        Integer count20,
        Integer count50,
        Integer count100,
        Integer count200
) {
}
