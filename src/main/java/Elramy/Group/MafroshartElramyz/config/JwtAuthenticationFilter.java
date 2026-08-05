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

            UserDetails userDetails =
                    userDetailsService
                            .loadUserByUsername(username);


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