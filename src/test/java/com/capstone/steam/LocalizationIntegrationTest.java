package com.capstone.steam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** Los textos de la API salen en el idioma de Accept-Language (es por defecto, en, it). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LocalizationIntegrationTest {

    private static final String BAD_LOGIN = "{\"login\":\"nadie\",\"password\":\"wrong-password\"}";

    @Autowired
    private MockMvc mvc;

    @Test
    void domainErrorsFollowAcceptLanguageAndDefaultToSpanish() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(BAD_LOGIN))
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
        mvc.perform(post("/api/auth/login").header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT")
                        .contentType(MediaType.APPLICATION_JSON).content(BAD_LOGIN))
                .andExpect(jsonPath("$.detail").value("Utente o password errati"));
        mvc.perform(post("/api/auth/login").header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON).content(BAD_LOGIN))
                .andExpect(jsonPath("$.detail").value("Wrong username or password"));
        // idioma no admitido -> español
        mvc.perform(post("/api/auth/login").header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
                        .contentType(MediaType.APPLICATION_JSON).content(BAD_LOGIN))
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));

        mvc.perform(get("/api/games/no-existe").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Game not found: no-existe"));
    }

    @Test
    void securityFilterErrorsAreTranslatedToo() throws Exception {
        mvc.perform(get("/api/users/me").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("A valid token is required"));
        mvc.perform(get("/api/users/me"))
                .andExpect(jsonPath("$.detail").value("Se requiere un token válido"));
    }

    @Test
    void validationMessagesAreTranslated() throws Exception {
        mvc.perform(post("/api/auth/register").header(HttpHeaders.ACCEPT_LANGUAGE, "it")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"no válido!","email":"x@test.com","password":"password123"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La richiesta contiene campi non validi"))
                .andExpect(jsonPath("$.errors.username").value("solo lettere, numeri, «_», «.» e «-»"));
    }

    @Test
    void gameDescriptionComesInTheRequestedLanguageWithSpanishFallback() throws Exception {
        String token = JsonPath.read(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"username":"studio_i18n","email":"studio@i18n.com","password":"password123","accountType":"DEVELOPER"}"""))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        Integer genreId = JsonPath.read(mvc.perform(get("/api/genres")).andReturn().getResponse().getContentAsString(),
                "$[0].id");
        String created = mvc.perform(post("/api/developer/games").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Políglota","description":"Hola","descriptionEn":"Hello","price":5,"genreIds":[%d]}"""
                        .formatted(genreId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descriptions.es").value("Hola"))
                .andExpect(jsonPath("$.descriptions.en").value("Hello"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");
        mvc.perform(post("/api/developer/games/" + id + "/publish").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/games/poliglota").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$.description").value("Hello"));
        // sin traducción al italiano -> la española
        mvc.perform(get("/api/games/poliglota").header(HttpHeaders.ACCEPT_LANGUAGE, "it"))
                .andExpect(jsonPath("$.description").value("Hola"));
    }
}
