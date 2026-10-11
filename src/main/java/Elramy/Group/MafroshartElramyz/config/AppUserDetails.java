package Elramy.Group.MafroshartElramyz.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.time.LocalDateTime;
import java.util.Collection;

/** Spring's user plus when the password/username last changed (V17). */
public class AppUserDetails extends User {

    private final LocalDateTime credentialsChangedAt;

    public AppUserDetails(String username, String password, boolean enabled,
                          Collection<? extends GrantedAuthority> authorities, LocalDateTime credentialsChangedAt) {
        super(username, password, enabled, true, true, true, authorities);
        this.credentialsChangedAt = credentialsChangedAt;
    }

    public LocalDateTime getCredentialsChangedAt() {
        return credentialsChangedAt;
    }
}
