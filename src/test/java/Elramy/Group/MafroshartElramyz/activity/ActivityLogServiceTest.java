package Elramy.Group.MafroshartElramyz.activity;

import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Who changed what, before -> after. */
class ActivityLogServiceTest {

    private final ActivityLogRepository repository = mock(ActivityLogRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ActivityLogService service = new ActivityLogService(repository, users);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void changesKeepOnlyRealDifferences() {
        List<Changes.Change> list = Changes.of()
                .add("السعر", new BigDecimal("100.00"), new BigDecimal("100"))   // same number
                .add("الاسم", "  ", null)                                       // blank = empty
                .add("الحالة", true, false)
                .add("الهاتف", "010", "011")
                .set("ملاحظات", null)                                            // nothing to show
                .list();

        assertEquals(List.of(new Changes.Change("الحالة", "نعم", "لا"), new Changes.Change("الهاتف", "010", "011")), list);
    }

    @Test
    void jsonRoundTripKeepsQuotesNewlinesAndNulls() {
        List<Changes.Change> list = List.of(
                new Changes.Change("ملاحظات", "سطر \"أول\"\nسطر ثانٍ {}", null),
                new Changes.Change("مسار", "a\\b,c:]", "\t\u0001"));
        assertEquals(list, ActivityLogService.parseJson(ActivityLogService.toJson(list)));
        assertTrue(ActivityLogService.parseJson(null).isEmpty());
    }

    @Test
    void recordsTheLoggedInUserAndTheChanges() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ahmed", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        User ahmed = new User();
        ahmed.setId(4L);
        ahmed.setUsername("ahmed");
        ahmed.setFullName("أحمد");
        when(users.findByUsername("ahmed")).thenReturn(Optional.of(ahmed));

        service.record(ActivityEntity.CUSTOMER, ActivityAction.UPDATE, 9L, "محمد (010)", "تعديل عميل",
                Changes.of().add("الهاتف", "010", "011"));

        ArgumentCaptor<ActivityLog> saved = ArgumentCaptor.forClass(ActivityLog.class);
        verify(repository).save(saved.capture());
        ActivityLog log = saved.getValue();
        assertEquals(4L, log.getUserId());
        assertEquals("أحمد", log.getUserFullName());
        assertEquals(ActivityAction.UPDATE, log.getAction());
        assertEquals(List.of(new Changes.Change("الهاتف", "010", "011")), ActivityLogService.parseJson(log.getChanges()));
    }

    @Test
    void noChangesAndNoUserStillRecords() {
        service.record(ActivityEntity.PRODUCT, ActivityAction.IMPORT, null, null, "استيراد", Changes.of());

        ArgumentCaptor<ActivityLog> saved = ArgumentCaptor.forClass(ActivityLog.class);
        verify(repository).save(saved.capture());
        assertNull(saved.getValue().getUserId());
        assertNull(saved.getValue().getChanges());
    }
}
