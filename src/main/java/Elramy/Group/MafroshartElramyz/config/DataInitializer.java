package Elramy.Group.MafroshartElramyz.config;

import Elramy.Group.MafroshartElramyz.enums.Role;
import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Creates the first admin on an empty install only, i.e. when no ADMIN
 * exists at all. It never recreates "admin1" after a rename or delete.
 *
 * The password comes from ADMIN_INITIAL_PASSWORD; without it a random
 * one is generated and printed once to the log. There is no built-in
 * password, because one in the source code is a password everyone knows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String INITIAL_ADMIN = "admin1";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_INITIAL_PASSWORD:}")
    private String initialPassword;

    @Override
    public void run(String... args) {
        if (userRepository.countByRole(Role.ADMIN) > 0 || userRepository.existsByUsername(INITIAL_ADMIN)) {
            return;
        }
        boolean generated = initialPassword == null || initialPassword.isBlank();
        String password = generated ? randomPassword() : initialPassword;

        userRepository.save(User.builder()
                .username(INITIAL_ADMIN)
                .password(passwordEncoder.encode(password))
                .fullName("System Administrator")
                .role(Role.ADMIN)
                .branch(null)
                .active(true)
                .build());

        if (generated) {
            log.warn("No admin existed: created '{}' with the one-time password: {}  (change it after the first login)",
                    INITIAL_ADMIN, password);
        } else {
            log.warn("No admin existed: created '{}' with ADMIN_INITIAL_PASSWORD. Change it after the first login.",
                    INITIAL_ADMIN);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
