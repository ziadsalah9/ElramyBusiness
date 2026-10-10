package Elramy.Group.MafroshartElramyz.productcolor;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** A ProductColorService stand-in with the colours seeded by V25. */
public final class TestColors {

    public static final List<String> NAMES = List.of("أبيض", "بيج", "رمادي", "أسود", "كحلي", "أحمر", "أخضر", "زيتي",
            "بني", "وردي", "أصفر", "برتقالي", "بنفسجي", "فيروزي", "ذهبي", "فضي", "متعدد الألوان");

    private TestColors() {
    }

    public static ProductColorService mock() {
        ProductColorService s = org.mockito.Mockito.mock(ProductColorService.class);
        when(s.activeNames()).thenReturn(NAMES);
        when(s.resolve(any())).thenAnswer(inv -> {
            String v = ProductColorService.canonical(inv.getArgument(0));
            return NAMES.contains(v) ? Optional.of(v) : Optional.empty();
        });
        when(s.requireUsable(any(), any())).thenAnswer(inv -> {
            String v = ProductColorService.canonical(inv.getArgument(0));
            if (v.isEmpty()) return null;
            if (!NAMES.contains(v)) throw new IllegalArgumentException("اللون «" + v + "» غير موجود في قائمة الألوان.");
            return v;
        });
        return s;
    }
}
