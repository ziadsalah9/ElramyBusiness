package Elramy.Group.MafroshartElramyz.productcolor;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.exception.ConflictException;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Colours (ADMIN changes them, everyone reads them), the same way as
 * product types. An item keeps its colour by name; renaming cascades to
 * items and drafts (V25). A colour in use cannot be deleted, only hidden.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductColorService {

    public static final int MAX_NAME = 50;
    private static final Pattern HEX = Pattern.compile("^#[0-9a-f]{6}$");

    private final ProductColorRepository colors;
    private final ActivityLogService activityLog;

    public record ColorResponse(Long id, String name, String hex, boolean active, long items, long drafts) {
    }

    @Transactional(readOnly = true)
    public List<ColorResponse> list(boolean includeHidden) {
        return colors.findAllByOrderByIdAsc().stream()
                .filter(c -> includeHidden || Boolean.TRUE.equals(c.getActive()))
                .map(c -> includeHidden ? response(c) : new ColorResponse(c.getId(), c.getName(), c.getHex(), true, 0, 0))
                .toList();
    }

    /** Names offered for new items (the Excel template list too). */
    @Transactional(readOnly = true)
    public List<String> activeNames() {
        return colors.findAllByOrderByIdAsc().stream().filter(c -> Boolean.TRUE.equals(c.getActive())).map(ProductColor::getName).toList();
    }

    /** The stored name for typed text (extra spaces ignored), if such a colour exists. */
    @Transactional(readOnly = true)
    public Optional<String> resolve(String raw) {
        String v = canonical(raw);
        if (v.isEmpty()) return Optional.empty();
        return colors.findByName(v).map(ProductColor::getName);
    }

    /** Typed text as a stored name would be written: single spaces, old aliases mapped. */
    public static String canonical(String raw) {
        String v = normalize(raw);
        return v.equals("متعدد") ? "متعدد الألوان" : v;   // the stored value before V25
    }

    /**
     * A colour for an item: blank means no colour; otherwise it must exist
     * and be shown, except that an item may keep the (now hidden) colour it
     * already has.
     */
    @Transactional(readOnly = true)
    public String requireUsable(String raw, String currentColor) {
        String v = normalize(raw);
        if (v.isEmpty()) return null;
        String name = resolve(v).orElseThrow(() -> new IllegalArgumentException("اللون «" + v + "» غير موجود في قائمة الألوان."));
        ProductColor c = colors.findByName(name).orElseThrow();
        if (!Boolean.TRUE.equals(c.getActive()) && !name.equals(currentColor)) {
            throw new IllegalArgumentException("اللون «" + name + "» مخفي ولا يُستخدم للأصناف الجديدة.");
        }
        return name;
    }

    public ColorResponse create(String rawName, String rawHex) {
        String name = validName(rawName, null);
        ProductColor c = colors.save(ProductColor.builder().name(name).hex(validHex(rawHex)).active(true).build());
        activityLog.record(ActivityEntity.PRODUCT, ActivityAction.CREATE, c.getId(), name, "إضافة لون «" + name + "»");
        return response(c);
    }

    public ColorResponse update(Long id, String rawName, String rawHex) {
        ProductColor c = find(id);
        String oldName = c.getName();
        String oldHex = c.getHex();
        String name = validName(rawName, id);
        String hex = validHex(rawHex);
        Changes changes = Changes.of().add("اللون", oldName, name).add("درجة اللون", oldHex, hex);
        c.setName(name);
        c.setHex(hex);
        colors.flush();                                     // items/drafts follow (ON UPDATE CASCADE)
        if (!changes.isEmpty()) {
            activityLog.record(ActivityEntity.PRODUCT, ActivityAction.UPDATE, c.getId(), name, "تعديل لون", changes);
        }
        return response(c);
    }

    public ColorResponse toggle(Long id) {
        ProductColor c = find(id);
        c.setActive(!Boolean.TRUE.equals(c.getActive()));
        activityLog.record(ActivityEntity.PRODUCT, ActivityAction.STATUS, c.getId(), c.getName(),
                (c.getActive() ? "إظهار" : "إخفاء") + " اللون «" + c.getName() + "»");
        return response(c);
    }

    public void delete(Long id) {
        ProductColor c = find(id);
        long items = colors.countVariants(c.getName());
        long drafts = colors.countDraftVariants(c.getName());
        if (items + drafts > 0) {
            throw new ConflictException("لا يمكن حذف اللون «" + c.getName() + "» لأنه مستخدم في " + items + " صنف"
                    + (drafts > 0 ? " و" + drafts + " صنف غير مكتمل" : "") + ". يمكنك إخفاؤه بدلاً من حذفه.");
        }
        colors.delete(c);
        activityLog.record(ActivityEntity.PRODUCT, ActivityAction.DELETE, id, c.getName(), "حذف اللون «" + c.getName() + "»");
    }

    private ColorResponse response(ProductColor c) {
        return new ColorResponse(c.getId(), c.getName(), c.getHex(), Boolean.TRUE.equals(c.getActive()),
                colors.countVariants(c.getName()), colors.countDraftVariants(c.getName()));
    }

    private String validName(String raw, Long id) {
        String name = normalize(raw);
        if (name.isEmpty()) throw new IllegalArgumentException("اكتب اسم اللون.");
        if (name.length() > MAX_NAME) throw new IllegalArgumentException("اسم اللون أطول من " + MAX_NAME + " حرف.");
        colors.findByName(name).filter(c -> !c.getId().equals(id)).ifPresent(c -> {
            throw new ConflictException("اللون «" + name + "» موجود بالفعل.");
        });
        return name;
    }

    static String validHex(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String hex = raw.trim().toLowerCase(Locale.ROOT);
        if (!HEX.matcher(hex).matches()) throw new IllegalArgumentException("درجة اللون يجب أن تكون بالشكل ‎#a1b2c3.");
        return hex;
    }

    private ProductColor find(Long id) {
        return colors.findById(id).orElseThrow(() -> new ResourceNotFoundException("اللون رقم " + id + " غير موجود."));
    }

    static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
