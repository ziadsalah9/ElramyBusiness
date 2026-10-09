package Elramy.Group.MafroshartElramyz.reports;

import Elramy.Group.MafroshartElramyz.customeraccount.CustomerPayment;
import Elramy.Group.MafroshartElramyz.customeraccount.CustomerPaymentRepository;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.PurchaseInvoice;
import Elramy.Group.MafroshartElramyz.models.SalesInvoice;
import Elramy.Group.MafroshartElramyz.models.SalesInvoiceItem;
import Elramy.Group.MafroshartElramyz.reports.ReportDtos.*;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import Elramy.Group.MafroshartElramyz.repository.PurchaseInvoiceRepository;
import Elramy.Group.MafroshartElramyz.repository.SalesInvoiceRepository;
import Elramy.Group.MafroshartElramyz.repository.StockTransactionRepository;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturn;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnItem;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnRepository;
import Elramy.Group.MafroshartElramyz.stocktake.StocktakeRepository;
import Elramy.Group.MafroshartElramyz.stocktake.StocktakeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Sales, profit and stock figures for a date range (ADMIN).
 *
 * Revenue per line is its total after the line discount, scaled by the
 * invoice discount (as returns are refunded). Profit uses the product's
 * current cost price, since sale lines do not store the cost at that time.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    static final int MAX_DAYS = 366;
    private static final int TOP = 10;
    private static final int SLOW_DAYS = 60;

    private final SalesInvoiceRepository invoices;
    private final SalesReturnRepository returns;
    private final CustomerPaymentRepository payments;
    private final PurchaseInvoiceRepository purchases;
    private final ProductRepository products;
    private final StockTransactionRepository ledger;
    private final StocktakeRepository stocktakes;
    private final Elramy.Group.MafroshartElramyz.expense.ExpenseRepository expenseRepository;
    private final Elramy.Group.MafroshartElramyz.installment.InstallmentRepository installmentPlans;

    public Report build(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new IllegalArgumentException("تاريخ النهاية قبل تاريخ البداية.");
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) throw new IllegalArgumentException("أقصى مدة للتقرير سنة واحدة.");
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        Map<LocalDate, DayAcc> days = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) days.put(d, new DayAcc());
        Map<Long, ProductAcc> byProduct = new HashMap<>();
        Map<PaymentMethod, PayAcc> byMethod = new EnumMap<>(PaymentMethod.class);

        BigDecimal gross = BigDecimal.ZERO, discounts = BigDecimal.ZERO, sales = BigDecimal.ZERO, cost = BigDecimal.ZERO, cash = BigDecimal.ZERO;
        int missingCost = 0;
        List<SalesInvoice> sold = invoices.findInRangeWithItems(start, end);
        for (SalesInvoice inv : sold) {
            DayAcc day = days.get(inv.getCreatedAt().toLocalDate());
            day.invoices++;
            BigDecimal linesTotal = inv.getItems().stream().map(SalesInvoiceItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ratio = linesTotal.signum() == 0 ? BigDecimal.ZERO : inv.getTotal().divide(linesTotal, 10, RoundingMode.HALF_UP);
            for (SalesInvoiceItem it : inv.getItems()) {
                BigDecimal lineGross = it.getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity()));
                BigDecimal revenue = it.getTotal().multiply(ratio);
                gross = gross.add(lineGross);
                BigDecimal lineCost = cost(it.getProduct(), it.getQuantity());
                if (lineCost == null) missingCost++;
                else cost = cost.add(lineCost);
                BigDecimal profit = lineCost == null ? BigDecimal.ZERO : revenue.subtract(lineCost);
                day.profit = day.profit.add(profit);
                ProductAcc p = byProduct.computeIfAbsent(it.getProduct().getId(), id -> new ProductAcc(it.getProduct()));
                p.quantity += it.getQuantity();
                p.revenue = p.revenue.add(revenue);
                p.profit = p.profit.add(profit);
            }
            sales = sales.add(inv.getTotal());
            day.sales = day.sales.add(inv.getTotal());
            if (inv.getCashAmount() != null) cash = cash.add(inv.getCashAmount());
            PayAcc m = byMethod.computeIfAbsent(inv.getPaymentMethod() == null ? PaymentMethod.CASH : inv.getPaymentMethod(), k -> new PayAcc());
            m.invoices++;
            m.paid = m.paid.add(inv.getPaid());
        }
        discounts = gross.subtract(sales);

        BigDecimal returned = BigDecimal.ZERO;
        for (SalesReturn r : returns.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end)) {
            returned = returned.add(r.getTotal());
            cash = cash.subtract(r.getCashRefund());
            DayAcc day = days.get(r.getCreatedAt().toLocalDate());
            day.returns = day.returns.add(r.getTotal());
            for (SalesReturnItem it : r.getItems()) {
                BigDecimal lineCost = cost(it.getProduct(), it.getQuantity());
                BigDecimal profit = lineCost == null ? BigDecimal.ZERO : it.getTotal().subtract(lineCost);
                if (lineCost != null) cost = cost.subtract(lineCost);
                day.profit = day.profit.subtract(profit);
                ProductAcc p = byProduct.computeIfAbsent(it.getProduct().getId(), id -> new ProductAcc(it.getProduct()));
                p.quantity -= it.getQuantity();
                p.revenue = p.revenue.subtract(it.getTotal());
                p.profit = p.profit.subtract(profit);
            }
        }

        BigDecimal paidLater = BigDecimal.ZERO;
        for (CustomerPayment p : payments.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end)) {
            paidLater = paidLater.add(p.getAmount());
            if (p.getPaymentMethod() == PaymentMethod.CASH) cash = cash.add(p.getAmount());
        }
        BigDecimal bought = purchases.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end).stream()
                .map(PurchaseInvoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = invoices.sumUnpaid().add(installmentPlans.sumAllMarkup()).subtract(payments.sumAll()).subtract(returns.sumAllCredits());

        BigDecimal net = sales.subtract(returned);
        BigDecimal profit = days.values().stream().map(d -> d.profit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal margin = net.signum() == 0 ? BigDecimal.ZERO : profit.multiply(BigDecimal.valueOf(100)).divide(net, 2, RoundingMode.HALF_UP);

        // Expenses by their own date (not when they were typed in).
        Map<Elramy.Group.MafroshartElramyz.expense.ExpenseCategory, ExpenseAcc> byCategory = new EnumMap<>(Elramy.Group.MafroshartElramyz.expense.ExpenseCategory.class);
        BigDecimal spent = BigDecimal.ZERO;
        for (Elramy.Group.MafroshartElramyz.expense.Expense e : expenseRepository.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(from, to)) {
            spent = spent.add(e.getAmount());
            ExpenseAcc acc = byCategory.computeIfAbsent(e.getCategory(), k -> new ExpenseAcc());
            acc.count++;
            acc.amount = acc.amount.add(e.getAmount());
        }

        Summary summary = new Summary(sold.size(), m(gross), m(discounts), m(returned), m(net), m(cost), m(profit), margin,
                m(cash), m(paidLater), m(bought), m(outstanding.max(BigDecimal.ZERO)), missingCost, m(spent), m(profit.subtract(spent)));

        List<Day> dayList = days.entrySet().stream().map(e -> new Day(e.getKey(), e.getValue().invoices, m(e.getValue().sales),
                m(e.getValue().returns), m(e.getValue().sales.subtract(e.getValue().returns)), m(e.getValue().profit))).toList();
        List<TopProduct> top = byProduct.values().stream()
                .filter(p -> p.quantity > 0)
                .sorted(Comparator.comparing((ProductAcc p) -> p.revenue).reversed())
                .limit(TOP)
                .map(p -> new TopProduct(p.product.getId(), p.product.getName(), p.product.getCode(), p.quantity, m(p.revenue), m(p.profit)))
                .toList();
        List<PaymentShare> methods = byMethod.entrySet().stream()
                .map(e -> new PaymentShare(e.getKey(), e.getValue().invoices, m(e.getValue().paid))).toList();
        List<StocktakeResult> counts = stocktakes
                .findByStatusAndApprovedAtGreaterThanEqualAndApprovedAtLessThanOrderByApprovedAtDesc(StocktakeStatus.APPROVED, start, end)
                .stream().map(s -> new StocktakeResult(s.getId(), s.getCode(), s.getTitle(), s.getApprovedAt(), s.getShortageCost(), s.getSurplusCost()))
                .toList();

        List<ExpenseShare> expenseShares = byCategory.entrySet().stream()
                .sorted(Map.Entry.<Elramy.Group.MafroshartElramyz.expense.ExpenseCategory, ExpenseAcc>comparingByValue(
                        Comparator.comparing((ExpenseAcc a) -> a.amount)).reversed())
                .map(e -> new ExpenseShare(e.getKey(), e.getValue().count, m(e.getValue().amount))).toList();
        return new Report(from, to, summary, dayList, top, slowMovers(), methods, counts, expenseShares);
    }

    /** Products in stock that have not sold for SLOW_DAYS days (or never), most stock value first. */
    private List<SlowMover> slowMovers() {
        Map<Long, LocalDateTime> lastSale = new HashMap<>();
        for (Object[] row : ledger.lastSalePerProduct()) lastSale.put((Long) row[0], (LocalDateTime) row[1]);
        LocalDateTime cutoff = LocalDate.now().minusDays(SLOW_DAYS).atStartOfDay();
        return products.findAll().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getActive()))
                .map(p -> new Object[]{p, p.activeVariants().stream().mapToInt(v -> v.getQuantity() == null ? 0 : v.getQuantity()).sum()})
                .filter(a -> (int) a[1] > 0)
                .filter(a -> { LocalDateTime last = lastSale.get(((Product) a[0]).getId()); return last == null || last.isBefore(cutoff); })
                .map(a -> {
                    Product p = (Product) a[0];
                    int stock = (int) a[1];
                    BigDecimal value = p.getPurchasePrice() == null ? null : m(p.getPurchasePrice().multiply(BigDecimal.valueOf(stock)));
                    return new SlowMover(p.getId(), p.getName(), p.getCode(), stock, value, lastSale.get(p.getId()));
                })
                .sorted(Comparator.comparing(SlowMover::stockValue, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .toList();
    }

    private static BigDecimal cost(Product p, int quantity) {
        return p.getPurchasePrice() == null ? null : p.getPurchasePrice().multiply(BigDecimal.valueOf(quantity));
    }

    private static BigDecimal m(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class DayAcc {
        int invoices;
        BigDecimal sales = BigDecimal.ZERO, returns = BigDecimal.ZERO, profit = BigDecimal.ZERO;
    }

    private static final class ProductAcc {
        final Product product;
        int quantity;
        BigDecimal revenue = BigDecimal.ZERO, profit = BigDecimal.ZERO;
        ProductAcc(Product product) { this.product = product; }
    }

    private static final class ExpenseAcc {
        int count;
        BigDecimal amount = BigDecimal.ZERO;
    }

    private static final class PayAcc {
        int invoices;
        BigDecimal paid = BigDecimal.ZERO;
    }
}
