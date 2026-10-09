package Elramy.Group.MafroshartElramyz.settings;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Transactional
public class StoreSettingsService {

    /** Used if the row is missing (it is seeded by V16). */
    static final BigDecimal DEFAULT_MAX_DISCOUNT = BigDecimal.TEN;

    private final StoreSettingsRepository repository;
    private final ActivityLogService activityLog;

    @Transactional(readOnly = true)
    public StoreSettingsDto get() {
        return toDto(load());
    }

    /** Largest discount percent an employee may give on one invoice. */
    @Transactional(readOnly = true)
    public BigDecimal employeeMaxDiscountPercent() {
        return repository.findById(StoreSettings.ID).map(StoreSettings::getEmployeeMaxDiscountPercent).orElse(DEFAULT_MAX_DISCOUNT);
    }

    public StoreSettingsDto update(StoreSettingsDto dto) {
        StoreSettings s = load();
        BigDecimal pct = dto.employeeMaxDiscountPercent().setScale(2, RoundingMode.HALF_UP);
        Changes changes = Changes.of()
                .add("اسم المحل", s.getStoreName(), dto.storeName().trim())
                .add("الهاتف", s.getPhone(), blank(dto.phone()))
                .add("العنوان", s.getAddress(), blank(dto.address()))
                .add("تذييل الإيصال", s.getReceiptFooter(), blank(dto.receiptFooter()))
                .add("حد خصم الموظف %", s.getEmployeeMaxDiscountPercent(), pct);
        s.setStoreName(dto.storeName().trim());
        s.setPhone(blank(dto.phone()));
        s.setAddress(blank(dto.address()));
        s.setReceiptFooter(blank(dto.receiptFooter()));
        s.setEmployeeMaxDiscountPercent(pct);
        StoreSettings saved = repository.save(s);
        if (!changes.isEmpty()) {
            activityLog.record(ActivityEntity.SETTINGS, ActivityAction.UPDATE, saved.getId(), "إعدادات المحل", "تعديل إعدادات المحل", changes);
        }
        return toDto(saved);
    }

    private StoreSettings load() {
        return repository.findById(StoreSettings.ID).orElseGet(() -> repository.save(StoreSettings.builder()
                .id(StoreSettings.ID).storeName("مفروشات الرامي").receiptFooter("شكراً لزيارتكم")
                .employeeMaxDiscountPercent(DEFAULT_MAX_DISCOUNT).build()));
    }

    private static StoreSettingsDto toDto(StoreSettings s) {
        return new StoreSettingsDto(s.getStoreName(), s.getPhone(), s.getAddress(), s.getReceiptFooter(), s.getEmployeeMaxDiscountPercent());
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
