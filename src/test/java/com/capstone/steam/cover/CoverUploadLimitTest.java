package com.capstone.steam.cover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jayway.jsonpath.JsonPath;

/**
 * Las peticiones que pasan del límite multipart tienen que llegar como 413 y no como un corte de
 * conexión: Tomcat necesita tragarse el resto del cuerpo (max-swallow-size). HTTP real, sin MockMvc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CoverUploadLimitTest {

    private static final String USER = "limit_cover_dev";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private CoverStorage storage;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from refresh_tokens where user_id in (select id from users where username = ?)", USER);
        jdbc.update("delete from users where username = ?", USER);
    }

    @Test
    void sevenMegabytesIs413AsAProblemDetail() throws Exception {
        assertThat(uploadStatusAndType(7 * 1024 * 1024)).containsExactly("413", "application/problem+json");
    }

    @Test
    void justOverTheFileLimitIs413AsAProblemDetail() throws Exception {
        assertThat(uploadStatusAndType(5 * 1024 * 1024 + 1)).containsExactly("413", "application/problem+json");
    }

    private String[] uploadStatusAndType(int fileBytes) throws Exception {
        when(storage.enabled()).thenReturn(true);
        HttpClient client = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;
        HttpResponse<String> reg = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","email":"%s@test.com","password":"password123","accountType":"DEVELOPER"}"""
                        .formatted(USER, USER))).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(reg.statusCode()).isEqualTo(201);
        String token = JsonPath.read(reg.body(), "$.accessToken");

        String boundary = "----limit" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"grande.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(new byte[fileBytes]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create(base + "/api/developer/games/1/cover"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
        String type = res.headers().firstValue("Content-Type").orElse("").split(";")[0];
        return new String[] {String.valueOf(res.statusCode()), type};
    }
}
