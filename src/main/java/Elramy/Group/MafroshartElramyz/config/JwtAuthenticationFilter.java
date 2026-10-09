package Elramy.Group.MafroshartElramyz.config;

import Elramy.Group.MafroshartElramyz.services.CustomUserDetailsService;
import Elramy.Group.MafroshartElramyz.services.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtService jwtService;

    private final CustomUserDetailsService userDetailsService;


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {


        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            filterChain.doFilter(request, response);
            return;
        }

        // =====================================================
        // GET AUTHORIZATION HEADER
        // =====================================================

        final String authHeader =
                request.getHeader("Authorization");


        // مفيش Authorization Header
        if (authHeader == null
                || !authHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);

            return;
        }


        // =====================================================
        // EXTRACT JWT
        // =====================================================

        String jwt =
                authHeader.substring(7);


        // =====================================================
        // EXTRACT USERNAME
        // =====================================================

        String username;

        try {

            username =
                    jwtService.extractUsername(jwt);

        } catch (Exception e) {

            // Token غير صالح
            filterChain.doFilter(request, response);

            return;
        }


        // =====================================================
        // CHECK USER NOT ALREADY AUTHENTICATED
        // =====================================================

        if (username != null
                && SecurityContextHolder
                .getContext()
                .getAuthentication() == null) {


            // =================================================
            // LOAD USER
            // =================================================
            // A deleted or missing user must produce 401, not 500.

            UserDetails userDetails;

            try {
                userDetails =
                        userDetailsService
                                .loadUserByUsername(username);
            } catch (Exception e) {
                filterChain.doFilter(request, response);
                return;
            }

            // =================================================
            // ENABLED CHECK
            // =================================================
            // A deactivated user must not stay authenticated
            // with a still-valid JWT until it expires.

            if (!userDetails.isEnabled()) {
                filterChain.doFilter(request, response);
                return;
            }

            // =================================================
            // VALIDATE TOKEN
            // =================================================

            if (jwtService.isTokenValid(
                    jwt,
                    userDetails)) {


                // =============================================
                // CREATE AUTHENTICATION
                // =============================================

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(

                                userDetails,

                                null,

                                userDetails.getAuthorities()
                        );


                // =============================================
                // REQUEST DETAILS
                // =============================================

                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );


                // =============================================
                // SET CURRENT USER
                // =============================================

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);
            }
        }


        // =====================================================
        // CONTINUE REQUEST
        // =====================================================

        filterChain.doFilter(request, response);
    }
}