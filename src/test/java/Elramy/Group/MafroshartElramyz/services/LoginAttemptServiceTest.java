package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.exception.LoginBlockedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/** Password guessing is cut off per username and per address, for a while. */
class LoginAttemptServiceTest {

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-09T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    void fiveWrongPasswordsBlockTheUsernameThenItExpires() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            assertDoesNotThrow(() -> service.checkAllowed("Admin1", "1.1.1.1"));
            service.loginFailed("admin1", "1.1.1." + i);   // different addresses: still the same account
        }
        assertThrows(LoginBlockedException.class, () -> service.checkAllowed(" ADMIN1 ", "9.9.9.9"));
        assertDoesNotThrow(() -> service.checkAllowed("cashier", "9.9.9.9"), "other accounts are not affected");

        clock.now = clock.now.plus(LoginAttemptService.BLOCK).plus(Duration.ofSeconds(1));
        assertDoesNotThrow(() -> service.checkAllowed("admin1", "1.1.1.1"));
    }

    @Test
    void oneAddressTryingManyUsernamesIsBlocked() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES_PER_ADDRESS; i++) {
            service.loginFailed("user" + i, "6.6.6.6");
        }
        assertThrows(LoginBlockedException.class, () -> service.checkAllowed("someone-new", "6.6.6.6"));
        assertDoesNotThrow(() -> service.checkAllowed("someone-new", "7.7.7.7"));
    }

    @Test
    void aSuccessfulLoginResetsTheUsernameCounter() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) service.loginFailed("admin1", "1.1.1.1");
        service.loginSucceeded("admin1");
        service.loginFailed("admin1", "1.1.1.1");
        assertDoesNotThrow(() -> service.checkAllowed("admin1", "2.2.2.2"));
    }
}
