package com.capstone.steam.auth;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** Flujo HTTP completo: filtro JWT, reglas por rol, validación y errores ProblemDetail. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void registerLoginAndGetProfile() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"alyx","email":"Alyx@BlackMesa.com","password":"password123","accountType":"DEVELOPER"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("alyx@blackmesa.com"))
                .andExpect(jsonPath("$.user.roles", containsInAnyOrder("ROLE_USER", "ROLE_DEVELOPER")));

        // login con el email en otro formato de mayúsculas
        String token = login("ALYX@blackmesa.com", "password123");

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alyx"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Alyx Vance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alyx Vance"));
    }

    @Test
    void protectedEndpointWithoutOrWithInvalidTokenReturns401Problem() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/users/me"));

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        register("breen", "breen@test.com");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"breen\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void invalidRegisterBodyReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a b\",\"email\":\"no-es-email\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registerStoresRequestedLocaleOrDefaultsToEs() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"gordon","email":"gordon@test.com","password":"password123","locale":"en"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.locale").value("en"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"barney","email":"barney@test.com","password":"password123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.locale").value("es"));
    }

    @Test
    void registerWithUnsupportedLocaleReturns400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"mossman","email":"mossman@test.com","password":"password123","locale":"fr"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.locale").exists());
    }

    @Test
    void updateProfileLocaleOnlyAffectsAuthenticatedUser() throws Exception {
        register("eli", "eli@test.com");
        register("judith", "judith@test.com");
        String eliToken = login("eli", "password123");
        String judithToken = login("judith", "password123");

        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + eliToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locale\":\"it\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("it"));

        // sin locale en el body no se modifica
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + eliToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Eli\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("it"));

        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + eliToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locale\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.locale").exists());

        // el otro usuario conserva su idioma
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + judithToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("es"));
    }

    // El @Pattern es estricto: MailTexts.of() tolera mayúsculas y espacios, pero la API solo guarda es|en|it
    @ParameterizedTest
    @ValueSource(strings = { "", " ", "EN", "It", " en", "en-US", "es|en" })
    void malformedLocaleIsRejectedOnRegisterAndOnProfileUpdate(String locale) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"vance","email":"vance@test.com","password":"password123","locale":"%s"}"""
                        .formatted(locale)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.locale").exists());

        String token = register("alyx", "alyx@test.com");
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locale\":\"%s\"}".formatted(locale)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.locale").exists());

        // el idioma guardado no cambia tras el 400
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("es"));
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        register("kleiner", "kleiner@test.com");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"KLEINER","email":"otro@test.com","password":"password123"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void roleRulesProtectAdminEndpoints() throws Exception {
        String userToken = register("barney", "barney@test.com");
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // el admin inicial lo crea AdminAccountInitializer con las credenciales del perfil test
        String adminToken = login("admin", "Admin12345!");
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username", hasItem("barney")));
    }

    @Test
    void adminPromotesUserToDeveloperAndCannotDemoteSelf() throws Exception {
        register("eli", "eli@test.com");
        String adminToken = login("admin", "Admin12345!");
        String eliId = JsonPath.read(mvc.perform(get("/api/admin/users?size=100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(), "$.content[?(@.username=='eli')].id").toString()
                .replaceAll("[\\[\\]]", "");

        mvc.perform(put("/api/admin/users/" + eliId + "/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ROLE_DEVELOPER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ROLE_USER", "ROLE_DEVELOPER")));

        String adminId = JsonPath.read(mvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(), "$.id").toString();
        mvc.perform(put("/api/admin/users/" + adminId + "/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void avatarUrlMustBeHttpOrHttps() throws Exception {
        String token = register("freeman", "freeman@blackmesa.com");

        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatarUrl\":\"ftp://a.test/a.png\"}"))
                .andExpect(status().isBadRequest());
        for (String spaced : new String[] {" https://a.test/a.png", "https://a.test/a.png "}) {
            mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"avatarUrl\":\"" + spaced + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatarUrl\":\"https://a.test/a.png\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void emptyAvatarUrlRemovesTheAvatar() throws Exception {
        String token = register("kleiner", "kleiner@blackmesa.com");
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatarUrl\":\"https://a.test/a.png\"}"))
                .andExpect(jsonPath("$.avatarUrl").value("https://a.test/a.png"));

        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatarUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    private String register(String username, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"password123"}""".formatted(username, email)))
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
