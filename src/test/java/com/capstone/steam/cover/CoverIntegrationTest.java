package com.capstone.steam.cover;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** Subida de portadas con Cloudinary simulado (nunca se llama a la API real). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoverIntegrationTest {

    private static final String COVER_URL = "https://res.cloudinary.com/demo/image/upload/arcade/covers/game-1.png";
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired private MockMvc mvc;
    @MockitoBean private CoverStorage storage;

    @BeforeEach
    void storageEnabled() {
        when(storage.enabled()).thenReturn(true);
        when(storage.upload(anyLong(), any(), any())).thenReturn(COVER_URL);
    }

    @Test
    void theOwnerUploadsACoverAndTheGameVersionGoesUp() throws Exception {
        String dev = register("maddy_cover", "DEVELOPER");
        String game = createGame(dev, "Cover Game");
        long id = ((Number) JsonPath.read(game, "$.id")).longValue();
        int version = JsonPath.read(game, "$.version");

        mvc.perform(multipart("/api/developer/games/" + id + "/cover").file(png("portada.png"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").value(COVER_URL))
                .andExpect(jsonPath("$.version").value(version + 1));
        verify(storage).upload(eq(id), any(), eq(ImageType.PNG));
    }

    @Test
    void anotherDeveloperAndAPlayerCannotUploadAndNothingIsSent() throws Exception {
        String owner = register("owner_cover", "DEVELOPER");
        long id = ((Number) JsonPath.read(createGame(owner, "Ajeno"), "$.id")).longValue();
        String other = register("other_cover", "DEVELOPER");
        String player = register("player_cover", "PLAYER");

        mvc.perform(multipart("/api/developer/games/" + id + "/cover").file(png("a.png"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/developer/games/" + id + "/cover").file(png("a.png"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + player))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/developer/games/999999/cover").file(png("a.png"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNotFound());
        verify(storage, never()).upload(anyLong(), any(), any());
    }

    @Test
    void aFileThatIsNotAnImageIs400EvenWithAnImageName() throws Exception {
        String dev = register("text_cover", "DEVELOPER");
        long id = ((Number) JsonPath.read(createGame(dev, "Texto"), "$.id")).longValue();
        MockMultipartFile text = new MockMultipartFile("file", "falsa.png", "image/png", "hola".getBytes());
        MockMultipartFile empty = new MockMultipartFile("file", "vacia.png", "image/png", new byte[0]);

        mvc.perform(multipart("/api/developer/games/" + id + "/cover").file(text)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(multipart("/api/developer/games/" + id + "/cover").file(empty)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev))
                .andExpect(status().isBadRequest());
        verify(storage, never()).upload(anyLong(), any(), any());
    }

    @Test
    void moreThanFiveMegabytesIs413AsAProblemDetail() throws Exception {
        String dev = register("big_cover", "DEVELOPER");
        long id = ((Number) JsonPath.read(createGame(dev, "Grande"), "$.id")).longValue();
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG_MAGIC, 0, big, 0, PNG_MAGIC.length);

        mvc.perform(multipart("/api/developer/games/" + id + "/cover")
                        .file(new MockMultipartFile("file", "grande.png", "image/png", big))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.detail").value("La portada no puede pesar más de 5 MB"));
        verify(storage, never()).upload(anyLong(), any(), any());
    }

    @Test
    void withoutCloudinaryTheUploadIs503AndTheStatusSaysDisabled() throws Exception {
        when(storage.enabled()).thenReturn(false);
        String dev = register("off_cover", "DEVELOPER");
        long id = ((Number) JsonPath.read(createGame(dev, "Sin Nube"), "$.id")).longValue();

        mvc.perform(multipart("/api/developer/games/" + id + "/cover").file(png("a.png"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/uploads/covers/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void theStatusNeedsASessionAndReportsEnabled() throws Exception {
        mvc.perform(get("/api/uploads/covers/status")).andExpect(status().isUnauthorized());
        String player = register("status_cover", "PLAYER");
        mvc.perform(get("/api/uploads/covers/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void anAdminUploadsToSomeoneElsesGameAndOthersCannot() throws Exception {
        String dev = register("dev_admin_cover", "DEVELOPER");
        long id = ((Number) JsonPath.read(createGame(dev, "Moderado"), "$.id")).longValue();
        String admin = login("admin", "Admin12345!");

        mvc.perform(multipart("/api/admin/games/" + id + "/cover").file(png("a.png"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").value(COVER_URL));
        mvc.perform(multipart("/api/admin/games/" + id + "/cover").file(png("a.png"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/games/999999/cover").file(png("a.png"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCloudinaryFailureIs502() throws Exception {
        when(storage.upload(anyLong(), any(), any())).thenThrow(new CoverUploadException(null));
        String dev = register("fail_cover", "DEVELOPER");
        long id = ((Number) JsonPath.read(createGame(dev, "Falla"), "$.id")).longValue();

        mvc.perform(multipart("/api/developer/games/" + id + "/cover").file(png("a.png"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    // ------------------------------------------------------------------

    private static MockMultipartFile png(String name) {
        byte[] bytes = Arrays.copyOf(PNG_MAGIC, 64);
        return new MockMultipartFile("file", name, "image/png", bytes);
    }

    private String createGame(String token, String title) throws Exception {
        Integer genreId = JsonPath.read(mvc.perform(get("/api/genres")).andReturn().getResponse().getContentAsString(),
                "$[0].id");
        return mvc.perform(post("/api/developer/games").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"%s","price":19.99,"genreIds":[%d]}""".formatted(title, genreId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String register(String username, String type) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@test.com","password":"password123","accountType":"%s"}"""
                        .formatted(username, username, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String login(String login, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
