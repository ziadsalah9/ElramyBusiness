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
        ) && !isTokenExpired(token) && !issuedBeforeCredentialsChange(token, userDetails);
    }


    /**
     * A token from before the last password/username change is revoked.
     * JWT times have second precision, so the change time is truncated:
     * a login in the same second as the change stays valid.
     */
    private boolean issuedBeforeCredentialsChange(String token, UserDetails userDetails) {
        if (!(userDetails instanceof Elramy.Group.MafroshartElramyz.config.AppUserDetails app)
                || app.getCredentialsChangedAt() == null) {
            return false;
        }
        Date issuedAt = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().getIssuedAt();
        Date changedAt = Date.from(app.getCredentialsChangedAt().withNano(0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        return issuedAt == null || issuedAt.before(changedAt);
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