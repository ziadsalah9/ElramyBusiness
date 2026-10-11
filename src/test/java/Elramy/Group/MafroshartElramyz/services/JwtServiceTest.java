package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.config.AppUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tokens from before a password/username change are revoked. */
class JwtServiceTest {

    // Test-only key (random letters, never used outside this test).
    private final JwtService jwt = new JwtService("t3st-only-key-9f8e7d6c5b4a39281706f5e4d3c2b1a0", 3_600_000);

    private static AppUserDetails user(LocalDateTime changedAt) {
        return new AppUserDetails("ahmed", "x", true, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), changedAt);
    }

    @Test
    void aTokenFromBeforeThePasswordChangeIsRejected() throws Exception {
        String old = jwt.generateToken(user(null));
        Thread.sleep(1100);   // JWT times have second precision
        LocalDateTime changed = LocalDateTime.now();

        assertFalse(jwt.isTokenValid(old, user(changed)));
        assertTrue(jwt.isTokenValid(jwt.generateToken(user(changed)), user(changed)), "logging in again works");
    }

    @Test
    void usersWithoutARecordedChangeKeepTheirTokens() {
        String token = jwt.generateToken(user(null));
        assertTrue(jwt.isTokenValid(token, user(null)));
        assertTrue(jwt.isTokenValid(token, user(LocalDateTime.now().minusDays(1))));
    }
}
