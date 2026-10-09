package Elramy.Group.MafroshartElramyz.producttype;

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
import java.util.Optional;

/**
 * Product types (ADMIN changes them, everyone reads them). A product keeps
 * its type by name; renaming cascades to products and drafts (V24). A type
 * in use cannot be deleted, only hidden.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ItemTypeService {

    public static final int MAX_NAME = 50;

    private final ItemTypeRepository types;
    private final ActivityLogService activityLog;

    public record ItemTypeResponse(Long id, String name, boolean active, long products, long drafts) {
    }

    @Transactional(readOnly = true)
    public List<ItemTypeResponse> list(boolean includeHidden) {
        return types.findAllByOrderByIdAsc().stream()
                .filter(t -> includeHidden || Boolean.TRUE.equals(t.getActive()))
                .map(t -> new ItemTypeResponse(t.getId(), t.getName(), Boolean.TRUE.equals(t.getActive()),
                        includeHidden ? types.countProducts(t.getName()) : 0, includeHidden ? types.countDrafts(t.getName()) : 0))
                .toList();
    }

    /** Names offered for new products (the Excel template list too). */
    @Transactional(readOnly = true)
    public List<String> activeNames() {
        return types.findAllByOrderByIdAsc().stream().filter(t -> Boolean.TRUE.equals(t.getActive())).map(ItemType::getName).toList();
    }

    /** The stored name for typed text (extra spaces ignored), if such a type exists. */
    @Transactional(readOnly = true)
    public Optional<String> resolve(String raw) {
        String v = normalize(raw);
        if (v.isEmpty()) return Optional.empty();
        if (v.equalsIgnoreCase("OTHER")) v = "أخرى";
        return types.findByName(v).map(ItemType::getName);
    }

    /**
     * A type for a product: must exist and be shown, except that a product
     * may keep the (now hidden) type it already has.
     */
    @Transactional(readOnly = true)
    public String requireUsable(String raw, String currentType) {
        String v = normalize(raw);
        if (v.isEmpty()) throw new IllegalArgumentException("اختر نوع المنتج.");
        ItemType t = types.findByName(v).orElseThrow(() -> new IllegalArgumentException("النوع «" + v + "» غير موجود."));
        if (!Boolean.TRUE.equals(t.getActive()) && !t.getName().equals(currentType)) {
            throw new IllegalArgumentException("النوع «" + v + "» مخفي ولا يُستخدم للمنتجات الجديدة.");
        }
        return t.getName();
    }

    public ItemTypeResponse create(String raw) {
        String name = validName(raw, null);
        ItemType t = types.save(ItemType.builder().name(name).active(true).build());
        activityLog.record(ActivityEntity.PRODUCT, ActivityAction.CREATE, t.getId(), name, "إضافة نوع منتج «" + name + "»");
        return new ItemTypeResponse(t.getId(), name, true, 0, 0);
    }

    public ItemTypeResponse rename(Long id, String raw) {
        ItemType t = find(id);
        String old = t.getName();
        String name = validName(raw, id);
        if (!name.equals(old)) {
            t.setName(name);
            types.flush();                                  // products/drafts follow (ON UPDATE CASCADE)
            types.renameInStocktakeScopes(old, name);
            activityLog.record(ActivityEntity.PRODUCT, ActivityAction.UPDATE, t.getId(), name, "تغيير اسم نوع منتج",
                    Changes.of().add("النوع", old, name));
        }
        return new ItemTypeResponse(t.getId(), t.getName(), Boolean.TRUE.equals(t.getActive()), types.countProducts(name), types.countDrafts(name));
    }

    public ItemTypeResponse toggle(Long id) {
        ItemType t = find(id);
        t.setActive(!Boolean.TRUE.equals(t.getActive()));
        activityLog.record(ActivityEntity.PRODUCT, ActivityAction.STATUS, t.getId(), t.getName(),
                (t.getActive() ? "إظهار" : "إخفاء") + " نوع المنتج «" + t.getName() + "»");
        return new ItemTypeResponse(t.getId(), t.getName(), t.getActive(), types.countProducts(t.getName()), types.countDrafts(t.getName()));
    }

    public void delete(Long id) {
        ItemType t = find(id);
        long products = types.countProducts(t.getName());
        long drafts = types.countDrafts(t.getName());
        if (products + drafts > 0) {
            throw new ConflictException("لا يمكن حذف النوع «" + t.getName() + "» لأنه مستخدم في " + products + " منتج"
                    + (drafts > 0 ? " و" + drafts + " منتج غير مكتمل" : "") + ". يمكنك إخفاؤه بدلاً من حذفه.");
        }
        types.delete(t);
        activityLog.record(ActivityEntity.PRODUCT, ActivityAction.DELETE, id, t.getName(), "حذف نوع المنتج «" + t.getName() + "»");
    }

    private String validName(String raw, Long id) {
        String name = normalize(raw);
        if (name.isEmpty()) throw new IllegalArgumentException("اكتب اسم النوع.");
        if (name.length() > MAX_NAME) throw new IllegalArgumentException("اسم النوع أطول من " + MAX_NAME + " حرف.");
        types.findByName(name).filter(t -> !t.getId().equals(id)).ifPresent(t -> {
            throw new ConflictException("النوع «" + name + "» موجود بالفعل.");
        });
        return name;
    }

    private ItemType find(Long id) {
        return types.findById(id).orElseThrow(() -> new ResourceNotFoundException("النوع رقم " + id + " غير موجود."));
    }

    static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
