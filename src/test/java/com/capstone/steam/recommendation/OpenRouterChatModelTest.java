package com.capstone.steam.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.capstone.steam.recommendation.dto.ChatMessage;
import com.capstone.steam.recommendation.dto.ChatMessage.Role;

import tools.jackson.databind.json.JsonMapper;

/** El cliente de OpenRouter contra un servidor simulado: nunca sale a la red. */
@ExtendWith(OutputCaptureExtension.class)
class OpenRouterChatModelTest {

    private static final String URL = "https://or.test/api/v1/chat/completions";
    private static final List<ChatMessage> MESSAGES = List.of(
            new ChatMessage(Role.user, "algo oscuro"),
            new ChatMessage(Role.assistant, "¿Y de qué tipo?"),
            new ChatMessage(Role.user, "con buena historia"));

    private MockRestServiceServer server;

    private OpenRouterChatModel model(String apiKey) {
        return model(apiKey, "acme/modelo:free");
    }

    private OpenRouterChatModel model(String apiKey, String models) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new OpenRouterChatModel(builder.build(), JsonMapper.builder().build(),
                new OpenRouterProperties(apiKey, models, "https://or.test/api/v1", Duration.ofSeconds(5), null, null));
    }

    @Test
    void sendsTheKeyTheModelTheSystemFirstAndTheJsonFormat() {
        OpenRouterChatModel model = model("test-key");
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(header("X-Title", "ARCADE"))
                .andExpect(jsonPath("$.model").value("acme/modelo:free"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("SISTEMA"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("algo oscuro"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[3].content").value("con buena historia"))
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.temperature").value(0.7))
                .andExpect(jsonPath("$.max_tokens").value(1200))
                .andExpect(jsonPath("$.reasoning.effort").value("low"))
                .andExpect(jsonPath("$.reasoning.exclude").value(true))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"{\\"reply\\":\\"hola\\"}"}}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(model.complete("SISTEMA", MESSAGES)).isEqualTo("{\"reply\":\"hola\"}");
        server.verify();
    }

    @Test
    void withoutAKeyItThrowsAndMakesNoRequest() {
        OpenRouterChatModel model = model("  ");
        server.expect(never(), requestTo(URL)).andRespond(withSuccess());

        assertThatThrownBy(() -> model.complete("s", MESSAGES)).isInstanceOf(AssistantUnavailableException.class);
        server.verify();
    }

    @Test
    void aQuotaOrServerErrorIsUnavailable() {
        OpenRouterChatModel model = model("k");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> model.complete("s", MESSAGES)).isInstanceOf(AssistantUnavailableException.class);
        assertThatThrownBy(() -> model.complete("s", MESSAGES)).isInstanceOf(AssistantUnavailableException.class);
        server.verify();
    }

    @Test
    void aQuota429IsToldApartFromOtherErrors() {
        OpenRouterChatModel model = model("k");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> model.complete("s", MESSAGES)).isInstanceOf(AssistantRateLimitedException.class);
        assertThatThrownBy(() -> model.complete("s", MESSAGES)).isInstanceOf(AssistantUnavailableException.class)
                .isNotInstanceOf(AssistantRateLimitedException.class);
    }

    @Test
    void aBodyWithoutChoicesOrContentIsUnavailable() {
        OpenRouterChatModel model = model("k");
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"error\":{\"message\":\"cupo\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":null}}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("esto no es json", MediaType.APPLICATION_JSON));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> model.complete("s", MESSAGES)).isInstanceOf(AssistantUnavailableException.class);
        }
        server.verify();
    }

    @Test
    void thePropertiesNeverPrintTheKey() {
        OpenRouterProperties properties = new OpenRouterProperties("sk-secreta-123", "m", "https://or.test", Duration.ofSeconds(1), null, null);

        assertThat(properties.toString()).doesNotContain("sk-secreta-123").contains("m");
        assertThat(properties.enabled()).isTrue();
        assertThat(new OpenRouterProperties("", "m", "u", Duration.ofSeconds(1), null, null).enabled()).isFalse();
        assertThat(new OpenRouterProperties(null, "m", "u", Duration.ofSeconds(1), null, null).enabled()).isFalse();
    }

    private static final String OK = """
            {"model":"b:free","choices":[{"message":{"role":"assistant","content":"{}"}}]}""";

    @Test
    void severalModelsGoAsAFallbackListWithoutModel() {
        OpenRouterChatModel model = model("k", "a:free, b:free");
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.models[0]").value("a:free"))
                .andExpect(jsonPath("$.models[1]").value("b:free"))
                .andExpect(jsonPath("$.models.length()").value(2))
                .andExpect(jsonPath("$.model").doesNotExist())
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        model.complete("s", MESSAGES);
        server.verify();
    }

    @Test
    void aSingleModelGoesAsModelWithoutModels() {
        OpenRouterChatModel model = model("k", " a:free ");
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.model").value("a:free"))
                .andExpect(jsonPath("$.models").doesNotExist())
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        model.complete("s", MESSAGES);
        server.verify();
    }

    @Test
    void theModelsAreSplitTrimmedAndWithoutEmpties() {
        OpenRouterProperties p = new OpenRouterProperties("k", " a:free,, b:free ,", "u", Duration.ofSeconds(1), null, null);

        assertThat(p.models()).containsExactly("a:free", "b:free");
    }

    @Test
    void aFailureLogsTheStatusAndMessageButNeverTheKey(CapturedOutput output) {
        OpenRouterChatModel model = model("sk-clave-secreta");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"Reasoning is mandatory\",\"code\":400}}"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"temporarily rate-limited upstream\"}}"));

        assertThatThrownBy(() -> model.complete("s", MESSAGES)).isInstanceOf(AssistantUnavailableException.class)
                .isNotInstanceOf(AssistantRateLimitedException.class);
        assertThatThrownBy(() -> model.complete("s", MESSAGES)).isInstanceOf(AssistantRateLimitedException.class);

        assertThat(output.getAll()).contains("400").contains("Reasoning is mandatory")
                .contains("429").contains("temporarily rate-limited upstream")
                .doesNotContain("sk-clave-secreta");
    }
}
