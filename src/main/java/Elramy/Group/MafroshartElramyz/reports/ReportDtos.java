package Elramy.Group.MafroshartElramyz.reports;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** The report for a date range (inclusive). Money in EGP, 2 decimals. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record Summary(
            int invoiceCount, BigDecimal grossSales, BigDecimal discounts, BigDecimal returns, BigDecimal netSales,
            /** Cost at the products' current cost price (sale lines do not store the cost). */
            BigDecimal costOfSales, BigDecimal grossProfit, BigDecimal profitMarginPercent,
            BigDecimal cashIn, BigDecimal customerPayments, BigDecimal purchases,
            /** What all customers owe right now (not limited to the range). */
            BigDecimal outstandingBalances,
            /** Sold lines whose product has no cost price: left out of the profit. */
            int linesWithoutCost) {
    }

    public record Day(LocalDate date, int invoices, BigDecimal sales, BigDecimal returns, BigDecimal net, BigDecimal profit) {
    }

    public record TopProduct(Long productId, String name, String code, int quantity, BigDecimal revenue, BigDecimal profit) {
    }

    public record SlowMover(Long productId, String name, String code, int stock, BigDecimal stockValue, LocalDateTime lastSaleAt) {
    }

    public record PaymentShare(PaymentMethod method, int invoices, BigDecimal paid) {
    }

    public record StocktakeResult(Long id, String code, String title, LocalDateTime approvedAt,
                                  BigDecimal shortageCost, BigDecimal surplusCost) {
    }

    public record Report(LocalDate from, LocalDate to, Summary summary, List<Day> days, List<TopProduct> topProducts,
                         List<SlowMover> slowMovers, List<PaymentShare> paymentMethods, List<StocktakeResult> stocktakes) {
    }
}
