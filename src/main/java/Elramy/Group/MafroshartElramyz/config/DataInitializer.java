package Elramy.Group.MafroshartElramyz.config;

import Elramy.Group.MafroshartElramyz.enums.Role;
import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        // =====================================================
        // CREATE DEFAULT ADMIN
        // =====================================================

        if (!userRepository.existsByUsername("admin1")) {

            User admin = User.builder()

                    .username("admin1")

                    .password(
                            passwordEncoder.encode("Admin@123")
                    )

                    .fullName("System Administrator")

                    .role(Role.ADMIN)

                    .branch(null)

                    .active(true)

                    .build();

            userRepository.save(admin);

            System.out.println(
                    "=========================================="
            );

            System.out.println(
                    "Default admin created"
            );

            System.out.println(
                    "Username: admin1"
            );

            System.out.println(
                    "Password: Admin@123"
            );

            System.out.println(
                    "=========================================="
            );
        }
    }
}