package Elramy.Group.MafroshartElramyz.config;

import Elramy.Group.MafroshartElramyz.services.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final CorsConfigurationSource corsConfigurationSource; // 👈 مجرد إضافة هذا السطر

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;


    // =========================================================
    // METHOD SECURITY / SUPERUSER
    // =========================================================
    // ADMIN is the system administrator and must be able to perform
    // any operation. Every @PreAuthorize in the controllers already
    // listed ADMIN, but only by convention - a new expression written
    // without ADMIN would silently lock the administrator out. This
    // handler makes ADMIN pass any role/authority/permission check,
    // so the guarantee does not depend on remembering to update every
    // annotation.

    @Bean
    public static MethodSecurityExpressionHandler methodSecurityExpressionHandler() {

        return new SuperuserMethodSecurityExpressionHandler();
    }


    // =========================================================
    // PASSWORD ENCODER
    // =========================================================

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }


    // =========================================================
    // AUTHENTICATION PROVIDER
    // =========================================================

    @Bean
    public AuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(
                        userDetailsService
                );

        provider.setPasswordEncoder(
                passwordEncoder()
        );

        return provider;
    }


    // =========================================================
    // AUTHENTICATION MANAGER
    // =========================================================

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }


    // =========================================================
    // SECURITY FILTER CHAIN
    // =========================================================

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http)
            throws Exception {

        http

                // =================================================
                // CSRF
                // =================================================

                .csrf(csrf ->
                        csrf.disable()
                )
                .cors(cors -> {})


                // =================================================
                // SESSION
                // =================================================

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )


                // =================================================
                // AUTHORIZATION
                // =================================================

                .authorizeHttpRequests(auth -> auth

                        // -----------------------------------------
                        // LOGIN
                        // -----------------------------------------

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Only login is public.
                        // "/me" must stay authenticated, so it is
                        // listed explicitly rather than covered by
                        // an "/api/auth/**" wildcard.
                        .requestMatchers(
                                "/api/auth/login"
                        ).permitAll()

                        .requestMatchers(
                                "/api/auth/me",
                                "/api/auth/change-password"
                        ).authenticated()


                        // -----------------------------------------
                        // SWAGGER
                        // -----------------------------------------

                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).hasRole("ADMIN")


                        // -----------------------------------------
                        // USER MANAGEMENT
                        // -----------------------------------------
                        // /api/users/** is intentionally NOT permitted
                        // here. Every method in UserController is
                        // guarded by @PreAuthorize("hasRole('ADMIN')"),
                        // so opening the path up would only weaken it.

                        // -----------------------------------------
                        // THE WEB APP ITSELF
                        // -----------------------------------------
                        // The built frontend (index.html, JS, CSS) is
                        // served by this application; its files are
                        // public, every /api call still needs a token.

                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()

                        // -----------------------------------------
                        // EVERYTHING ELSE
                        // -----------------------------------------

                        .anyRequest()
                        .authenticated()
                )


                // =================================================
                // ENTRY POINT
                // =================================================
                // Reject anonymous requests with 401, not 403, so the
                // client can tell "log in again" apart from "forbidden".

                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(
                                authenticationEntryPoint
                        )
                )


                // =================================================
                // AUTHENTICATION PROVIDER
                // =================================================

                .authenticationProvider(
                        authenticationProvider()
                )


                // =================================================
                // JWT FILTER
                // =================================================

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );


        return http.build();
    }
}