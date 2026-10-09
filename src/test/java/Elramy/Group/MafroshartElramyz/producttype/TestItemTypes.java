package Elramy.Group.MafroshartElramyz.producttype;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** An ItemTypeService stand-in with the 16 types seeded by V24. */
public final class TestItemTypes {

    public static final List<String> NAMES = List.of("لحاف", "بطانيه", "طقم", "فوط", "دفاية", "برنس", "مفارش", "حافظة",
            "مشاية", "كوفرتة", "مصلية", "نيش", "سفرة", "انترية", "سراحة", "أخرى");

    private TestItemTypes() {
    }

    public static ItemTypeService mock() {
        ItemTypeService s = org.mockito.Mockito.mock(ItemTypeService.class);
        when(s.activeNames()).thenReturn(NAMES);
        when(s.resolve(any())).thenAnswer(inv -> {
            String v = ItemTypeService.normalize(inv.getArgument(0));
            if (v.equalsIgnoreCase("OTHER")) v = "أخرى";
            return NAMES.contains(v) ? Optional.of(v) : Optional.empty();
        });
        when(s.requireUsable(any(), any())).thenAnswer(inv -> {
            String v = ItemTypeService.normalize(inv.getArgument(0));
            if (!NAMES.contains(v)) throw new IllegalArgumentException("النوع «" + v + "» غير موجود.");
            return v;
        });
        return s;
    }
}
