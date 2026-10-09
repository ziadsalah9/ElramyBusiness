package Elramy.Group.MafroshartElramyz.integration;

import Elramy.Group.MafroshartElramyz.enums.Role;
import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A signed-in user changes their own password over the real HTTP API:
 * wrong current password (400, still signed in), weak new password,
 * then a successful change that revokes the old login and returns a new one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChangePasswordIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void changeOwnPassword() throws Exception {
        String username = "pw-" + UUID.randomUUID().toString().substring(0, 8);
        users.save(User.builder().username(username).password(encoder.encode("OldPass1234"))
                .fullName("Password Test").role(Role.ADMIN).active(true).build());

        String oldToken = token(login(username, "OldPass1234"));
        // JWT times are in whole seconds: let the change happen in a later second.
        Thread.sleep(1100);

        HttpResponse<String> wrong = change(oldToken, "NotMyPass99", "NewPass5678");
        assertEquals(400, wrong.statusCode(), wrong.body());
        assertTrue(wrong.body().contains("كلمة المرور الحالية غير صحيحة"), wrong.body());
        assertEquals(200, me(oldToken), "a wrong current password must not sign the user out");

        assertEquals(400, change(oldToken, "OldPass1234", "short1").statusCode(), "password policy applies");
        assertEquals(400, change(oldToken, "OldPass1234", "OldPass1234").statusCode(), "must differ from the current one");

        HttpResponse<String> ok = change(oldToken, "OldPass1234", "NewPass5678");
        assertEquals(200, ok.statusCode(), ok.body());
        String newToken = token(ok);

        assertEquals(401, me(oldToken), "the old login is revoked");
        assertEquals(200, me(newToken), "the returned login works");
        assertEquals(401, login(username, "OldPass1234").statusCode());
        assertEquals(200, login(username, "NewPass5678").statusCode());
    }

    @Test
    void needsSignIn() throws Exception {
        assertEquals(401, change(null, "a", "b").statusCode());
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        return post("/api/auth/login", null, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private HttpResponse<String> change(String token, String current, String next) throws Exception {
        return post("/api/auth/change-password", token, "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
    }

    private int me(String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private HttpResponse<String> post(String path, String token, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        Matcher m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
        assertTrue(m.find(), response.body());
        return m.group(1);
    }
}
