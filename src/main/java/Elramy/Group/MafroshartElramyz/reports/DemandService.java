package Elramy.Group.MafroshartElramyz.reports;

import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sales and demand per colour/size over the last N days: how much sold
 * (after returns), how fast, the trend, how long the stock lasts, an ABC
 * class by revenue and what to do about it (reorder, sold out, overstock,
 * not moving). ADMIN only (uses cost prices).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemandService {

    /** Below this many days of stock, an item that sells should be reordered. */
    static final int REORDER_DAYS = 14;
    /** Above this many days of stock, there is too much of it. */
    static final int OVERSTOCK_DAYS = 180;
    /** Reorder suggestions aim at this many days of stock. */
    static final int TARGET_DAYS = 30;

    private final ProductVariantRepository variants;
    private final NamedParameterJdbcTemplate jdbc;

    public enum Abc { A, B, C }

    public enum Status {
        /** Sold in the period, none left. */
        OUT_OF_STOCK,
        /** Sells, and the stock lasts less than REORDER_DAYS. */
        REORDER,
        OK,
        /** Sells, but the stock lasts more than OVERSTOCK_DAYS. */
        OVERSTOCK,
        /** In stock, nothing sold in the period. */
        NOT_MOVING,
        /** No stock and no sales. */
        NO_ACTIVITY
    }

    public record Item(Long variantId, Long productId, String productName, String code, String itemType, String label,
                       int stock, BigDecimal stockValue, int sold, int returned, int net, BigDecimal revenue,
                       BigDecimal avgDaily, int firstHalf, int secondHalf, Integer daysOfCover, LocalDateTime lastSaleAt,
                       Abc abc, Status status, int suggestedReorder) {
    }

    public record Report(LocalDate from, LocalDate to, int days, List<Item> items) {
    }

    static final class Acc {
        int sold, returned, soldLate, returnedLate;
        BigDecimal revenue = BigDecimal.ZERO;
    }

    public Report build(int days) {
        int d = Math.max(7, Math.min(days, 365));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(d - 1L);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        LocalDateTime mid = start.plusSeconds(java.time.Duration.between(start, end).getSeconds() / 2);
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("start", Timestamp.valueOf(start))
                .addValue("end", Timestamp.valueOf(end)).addValue("mid", Timestamp.valueOf(mid));

        Map<Long, Acc> acc = new HashMap<>();
        // Revenue = each line's share of the invoice total (after the invoice discount), like ReportService.
        jdbc.query("""
                SELECT si.variant_id AS v,
                       SUM(si.quantity) AS qty,
                       SUM(CASE WHEN t.lines = 0 THEN 0 ELSE si.total * inv.total / t.lines END) AS revenue,
                       COALESCE(SUM(si.quantity) FILTER (WHERE inv.created_at >= :mid), 0) AS late
                FROM sales_invoice_items si
                JOIN sales_invoices inv ON inv.id = si.invoice_id
                JOIN (SELECT invoice_id, SUM(total) AS lines FROM sales_invoice_items GROUP BY invoice_id) t ON t.invoice_id = inv.id
                WHERE inv.created_at >= :start AND inv.created_at < :end AND si.variant_id IS NOT NULL
                GROUP BY si.variant_id
                """, p, rs -> {
            Acc a = acc.computeIfAbsent(rs.getLong("v"), k -> new Acc());
            a.sold = rs.getInt("qty");
            a.soldLate = rs.getInt("late");
            a.revenue = rs.getBigDecimal("revenue");
        });
        jdbc.query("""
                SELECT ri.variant_id AS v, SUM(ri.quantity) AS qty, SUM(ri.total) AS refund,
                       COALESCE(SUM(ri.quantity) FILTER (WHERE r.created_at >= :mid), 0) AS late
                FROM sales_return_items ri
                JOIN sales_returns r ON r.id = ri.return_id
                WHERE r.created_at >= :start AND r.created_at < :end AND ri.variant_id IS NOT NULL
                GROUP BY ri.variant_id
                """, p, rs -> {
            Acc a = acc.computeIfAbsent(rs.getLong("v"), k -> new Acc());
            a.returned = rs.getInt("qty");
            a.returnedLate = rs.getInt("late");
            a.revenue = a.revenue.subtract(rs.getBigDecimal("refund"));
        });
        Map<Long, LocalDateTime> lastSale = new HashMap<>();
        jdbc.query("""
                SELECT si.variant_id AS v, MAX(inv.created_at) AS last
                FROM sales_invoice_items si JOIN sales_invoices inv ON inv.id = si.invoice_id
                WHERE si.variant_id IS NOT NULL GROUP BY si.variant_id
                """, p, rs -> {
            lastSale.put(rs.getLong("v"), rs.getTimestamp("last").toLocalDateTime());
        });

        List<Item> items = new ArrayList<>();
        for (ProductVariant v : variants.findAllCountable()) {
            items.add(item(v, acc.getOrDefault(v.getId(), new Acc()), lastSale.get(v.getId()), d));
        }
        return new Report(from, to, d, classify(items));
    }

    static Item item(ProductVariant v, Acc a, LocalDateTime lastSale, int days) {
        Product p = v.getProduct();
        int stock = Math.max(0, v.getQuantity() == null ? 0 : v.getQuantity());
        int net = a.sold - a.returned;
        int secondHalf = a.soldLate - a.returnedLate;
        int firstHalf = net - secondHalf;
        BigDecimal avg = BigDecimal.valueOf(Math.max(net, 0)).divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP);
        Integer cover = avg.signum() == 0 ? null
                : BigDecimal.valueOf(stock).divide(avg, 0, RoundingMode.FLOOR).min(BigDecimal.valueOf(9999)).intValue();
        int suggested = avg.signum() == 0 ? 0
                : Math.max(0, avg.multiply(BigDecimal.valueOf(TARGET_DAYS)).setScale(0, RoundingMode.CEILING).intValue() - stock);
        BigDecimal cost = p.getPurchasePrice() == null ? BigDecimal.ZERO : p.getPurchasePrice();
        Status status = net > 0
                ? stock == 0 ? Status.OUT_OF_STOCK
                : cover < REORDER_DAYS ? Status.REORDER
                : cover > OVERSTOCK_DAYS ? Status.OVERSTOCK : Status.OK
                : stock > 0 ? Status.NOT_MOVING : Status.NO_ACTIVITY;
        return new Item(v.getId(), p.getId(), p.getName(), v.getCode(), p.getItemType(), v.label(), stock,
                cost.multiply(BigDecimal.valueOf(stock)).setScale(2, RoundingMode.HALF_UP), a.sold, a.returned, net,
                a.revenue.setScale(2, RoundingMode.HALF_UP), avg.setScale(2, RoundingMode.HALF_UP), firstHalf, secondHalf,
                cover, lastSale, null, status, suggested);
    }

    /**
     * ABC by revenue: the items that make the first 80% of revenue are A,
     * up to 95% B, the rest that sold C. Sorted by revenue, best first;
     * items that did not sell follow, most stock value first.
     */
    static List<Item> classify(List<Item> items) {
        List<Item> selling = new ArrayList<>(items.stream().filter(i -> i.revenue().signum() > 0).toList());
        selling.sort(Comparator.comparing(Item::revenue).reversed());
        BigDecimal total = selling.stream().map(Item::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Item> out = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Item i : selling) {
            BigDecimal before = running.multiply(BigDecimal.valueOf(100)).divide(total, 4, RoundingMode.HALF_UP);
            running = running.add(i.revenue());
            Abc abc = before.compareTo(BigDecimal.valueOf(80)) < 0 ? Abc.A : before.compareTo(BigDecimal.valueOf(95)) < 0 ? Abc.B : Abc.C;
            out.add(withAbc(i, abc));
        }
        items.stream().filter(i -> i.revenue().signum() <= 0)
                .sorted(Comparator.comparing(Item::stockValue).reversed())
                .forEach(out::add);
        return out;
    }

    private static Item withAbc(Item i, Abc abc) {
        return new Item(i.variantId(), i.productId(), i.productName(), i.code(), i.itemType(), i.label(), i.stock(), i.stockValue(),
                i.sold(), i.returned(), i.net(), i.revenue(), i.avgDaily(), i.firstHalf(), i.secondHalf(), i.daysOfCover(),
                i.lastSaleAt(), abc, i.status(), i.suggestedReorder());
    }
}
