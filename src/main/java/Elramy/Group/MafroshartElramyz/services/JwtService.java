package Elramy.Group.MafroshartElramyz.services;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;

    private final long expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {

        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.expiration = expiration;
    }


    // =========================================================
    // GENERATE TOKEN
    // =========================================================

    public String generateToken(
            UserDetails userDetails) {

        Date now = new Date();

        Date expiry =
                new Date(
                        now.getTime() + expiration
                );

        return Jwts.builder()

                .subject(
                        userDetails.getUsername()
                )

                .issuedAt(now)

                .expiration(expiry)

                .signWith(secretKey)

                .compact();
    }


    // =========================================================
    // EXTRACT USERNAME
    // =========================================================

    public String extractUsername(
            String token) {

        return Jwts.parser()

                .verifyWith(secretKey)

                .build()

                .parseSignedClaims(token)

                .getPayload()

                .getSubject();
    }


    // =========================================================
    // VALIDATE TOKEN
    // =========================================================

    public boolean isTokenValid(
            String token,
            UserDetails userDetails) {

        String username =
                extractUsername(token);

        return username.equals(
                userDetails.getUsername()
        ) && !isTokenExpired(token);
    }


    // =========================================================
    // CHECK EXPIRATION
    // =========================================================

    private boolean isTokenExpired(
            String token) {

        Date expirationDate =
                Jwts.parser()

                        .verifyWith(secretKey)

                        .build()

                        .parseSignedClaims(token)

                        .getPayload()

                        .getExpiration();

        return expirationDate.before(
                new Date()
        );
    }
}