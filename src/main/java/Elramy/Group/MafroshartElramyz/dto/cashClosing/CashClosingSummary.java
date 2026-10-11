package Elramy.Group.MafroshartElramyz.dto.cashClosing;

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
        Integer count200,

        /** The day's profit (ADMIN only; null for employees, who do not see costs). */
        DayProfit dayProfit
) {

    /**
     * Profit of the closing day: sales after returns minus the cost of what
     * was sold, minus that day's expenses. The screen adds the drawer
     * difference (counted - expected) to show the actual earning.
     */
    public record DayProfit(BigDecimal netSales, BigDecimal costOfSales, BigDecimal grossProfit, BigDecimal expenses,
                            BigDecimal netProfit, int linesWithoutCost) {
    }
}
