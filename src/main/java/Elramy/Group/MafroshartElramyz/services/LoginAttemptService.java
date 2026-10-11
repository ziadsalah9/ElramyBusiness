package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.exception.LoginBlockedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after {@value #MAX_FAILURES} failed logins
 * for the same username, or {@value #MAX_FAILURES_PER_ADDRESS} from the
 * same address, further attempts are refused for {@link #BLOCK}. Kept in
 * memory: one store, one server, and a restart only resets the counters.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final int MAX_FAILURES_PER_ADDRESS = 20;
    static final Duration BLOCK = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant firstFailure) { }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Throws when this username or address is currently blocked. */
    public void checkAllowed(String username, String address) {
        if (blocked(userKey(username), MAX_FAILURES) || blocked(addressKey(address), MAX_FAILURES_PER_ADDRESS)) {
            throw new LoginBlockedException("محاولات دخول خاطئة كثيرة. حاول مرة أخرى بعد " + BLOCK.toMinutes() + " دقيقة.");
        }
    }

    public void loginFailed(String username, String address) {
        record(userKey(username));
        record(addressKey(address));
        if (attempts.size() > 10_000) purgeExpired();
    }

    public void loginSucceeded(String username) {
        attempts.remove(userKey(username));
    }

    private boolean blocked(String key, int max) {
        Attempts a = attempts.get(key);
        if (a == null) return false;
        if (expired(a)) {
            attempts.remove(key, a);
            return false;
        }
        return a.failures() >= max;
    }

    private void record(String key) {
        Instant now = clock.instant();
        attempts.compute(key, (k, a) -> a == null || expired(a) ? new Attempts(1, now) : new Attempts(a.failures() + 1, a.firstFailure()));
    }

    private boolean expired(Attempts a) {
        return a.firstFailure().plus(BLOCK).isBefore(clock.instant());
    }

    private void purgeExpired() {
        attempts.values().removeIf(this::expired);
    }

    private static String userKey(String username) {
        return "u:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String addressKey(String address) {
        return "a:" + (address == null ? "" : address);
    }
}
