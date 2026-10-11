package Elramy.Group.MafroshartElramyz.reports;

import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.reports.DemandService.Abc;
import Elramy.Group.MafroshartElramyz.reports.DemandService.Item;
import Elramy.Group.MafroshartElramyz.reports.DemandService.Status;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Demand figures for one colour/size, the status rules and the ABC classes. */
class DemandServiceTest {

    private static long ids = 1;

    /** An item with {@code stock} left that sold {@code sold} (returned {@code returned}) in 30 days, {@code late} of them in the second half. */
    private static Item item(int stock, int sold, int returned, int late, String revenue) {
        Product p = Product.builder().id(ids).name("لحاف " + ids).itemType("لحاف").purchasePrice(new BigDecimal("50")).build();
        ProductVariant v = ProductVariant.builder().id(ids++).product(p).code("K0000" + ids).quantity(stock).build();
        DemandService.Acc a = new DemandService.Acc();
        a.sold = sold;
        a.returned = returned;
        a.soldLate = late;
        a.revenue = new BigDecimal(revenue);
        return DemandService.item(v, a, null, 30);
    }

    @Test
    void figuresForOneItem() {
        Item i = item(12, 32, 2, 20, "3000");          // 30 net in 30 days -> 1 a day
        assertEquals(30, i.net());
        assertEquals(0, new BigDecimal("1.00").compareTo(i.avgDaily()));
        assertEquals(12, i.daysOfCover());               // 12 in stock at 1 a day
        assertEquals(Status.REORDER, i.status());        // under 14 days
        assertEquals(18, i.suggestedReorder());          // 30 days' worth (30) - 12 in stock
        assertEquals(10, i.firstHalf());                 // 30 - 20: selling faster lately
        assertEquals(20, i.secondHalf());
        assertEquals(0, new BigDecimal("600.00").compareTo(i.stockValue()));   // 12 x 50
    }

    @Test
    void statusRules() {
        assertEquals(Status.OUT_OF_STOCK, item(0, 5, 0, 2, "500").status());
        assertEquals(Status.OK, item(30, 30, 0, 15, "500").status());          // 30 days of stock
        assertEquals(Status.OVERSTOCK, item(400, 30, 0, 15, "500").status());  // 400 days
        assertEquals(Status.NOT_MOVING, item(7, 0, 0, 0, "0").status());
        assertEquals(Status.NO_ACTIVITY, item(0, 0, 0, 0, "0").status());
        Item allBack = item(3, 2, 2, 0, "0");                                  // sold and returned: no demand
        assertEquals(Status.NOT_MOVING, allBack.status());
        assertNull(allBack.daysOfCover());
        assertEquals(0, allBack.suggestedReorder());
    }

    @Test
    void abcByShareOfRevenue() {
        List<Item> items = new ArrayList<>(List.of(
                item(5, 10, 0, 5, "100"), item(5, 10, 0, 5, "700"), item(5, 10, 0, 5, "150"),
                item(5, 10, 0, 5, "50"), item(9, 0, 0, 0, "0")));
        List<Item> out = DemandService.classify(items);

        assertEquals(List.of("700", "150", "100", "50", "0"), out.stream().map(i -> i.revenue().stripTrailingZeros().toPlainString()).toList());
        // 700 starts at 0% -> A; 150 starts at 70% -> A; 100 at 85% -> B; 50 at 95% -> C; no sales -> no class.
        assertEquals(java.util.Arrays.asList(Abc.A, Abc.A, Abc.B, Abc.C, null), out.stream().map(Item::abc).toList());
    }
}
