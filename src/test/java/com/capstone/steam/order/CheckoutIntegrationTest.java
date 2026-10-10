package com.capstone.steam.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.notification.event.OrderPaidEvent;
import com.capstone.steam.notification.event.PaymentFailedEvent;
import com.jayway.jsonpath.JsonPath;

/** Checkout completo por HTTP: carrito -> pedido -> pago -> biblioteca. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class CheckoutIntegrationTest {

    private static final String GOOD_CARD = "{\"method\":\"CREDIT_CARD\",\"cardNumber\":\"4242424242424242\"}";
    private static final String DECLINED_CARD = "{\"method\":\"CREDIT_CARD\",\"cardNumber\":\"4000000000000000\"}";

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationEvents events;

    private String devToken;
    private String playerToken;
    private int celesteId;
    private int hadesId;

    @BeforeEach
    void setUp() throws Exception {
        devToken = register("studio", "studio@test.com", "DEVELOPER");
        playerToken = register("player", "player@test.com", "PLAYER");
        celesteId = publishGame("Celeste", "20.00", 25); // 15.00
        hadesId = publishGame("Hades", "24.99", 0);
    }

    @Test
    void successfulCheckoutDeliversGamesAndClearsCartAndWishlist() throws Exception {
        addToCart(celesteId);
        addToCart(hadesId);
        mvc.perform(auth(put("/api/wishlist/" + celesteId), playerToken)).andExpect(status().isNoContent());

        int orderId = createOrder()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(39.99))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.lastPayment").doesNotExist())
                .andReturn().getResponse().getContentAsString().transform(b -> JsonPath.read(b, "$.id"));

        mvc.perform(auth(post("/api/orders/" + orderId + "/pay"), playerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(GOOD_CARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.lastPayment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.lastPayment.reference").value(org.hamcrest.Matchers.startsWith("SIM-")));

        mvc.perform(auth(get("/api/library"), playerToken))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].orderId").value(orderId));
        mvc.perform(auth(get("/api/cart"), playerToken)).andExpect(jsonPath("$.itemCount").value(0));
        mvc.perform(auth(get("/api/wishlist"), playerToken)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/games/celeste")).andExpect(jsonPath("$.salesCount").value(1));

        assertThat(events.stream(OrderPaidEvent.class))
                .singleElement()
                .satisfies(e -> assertThat(e.lines())
                        .extracting(OrderPaidEvent.Line::title, line -> line.unitPrice().stripTrailingZeros().toPlainString())
                        .containsExactlyInAnyOrder(tuple("Celeste", "15"), tuple("Hades", "24.99")));

        // doble clic en "pagar" -> el pedido ya no está PENDING
        mvc.perform(auth(post("/api/orders/" + orderId + "/pay"), playerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(GOOD_CARD))
                .andExpect(status().isConflict());
    }

    @Test
    void declinedPaymentReturns402KeepsCartAndRecordsFailure() throws Exception {
        addToCart(celesteId);
        int orderId = createOrder().andReturn().getResponse().getContentAsString()
                .transform(b -> JsonPath.read(b, "$.id"));

        mvc.perform(auth(post("/api/orders/" + orderId + "/pay"), playerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(DECLINED_CARD))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.lastPayment.status").value("FAILED"))
                .andExpect(jsonPath("$.lastPayment.failureReason").value("Tarjeta rechazada por el emisor"));

        mvc.perform(auth(get("/api/cart"), playerToken)).andExpect(jsonPath("$.itemCount").value(1));
        mvc.perform(auth(get("/api/library"), playerToken)).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(events.stream(PaymentFailedEvent.class)).hasSize(1);
        assertThat(events.stream(OrderPaidEvent.class)).isEmpty();

        // se puede reintentar con un pedido nuevo
        int retryId = createOrder().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .transform(b -> JsonPath.read(b, "$.id"));
        mvc.perform(auth(post("/api/orders/" + retryId + "/pay"), playerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"PAYPAL\"}"))
                .andExpect(status().isOk());

        mvc.perform(auth(get("/api/orders"), playerToken))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.content[1].status").value("FAILED"));
    }

    @Test
    void pendingOrderCanBeCancelledOnce() throws Exception {
        addToCart(hadesId);
        int orderId = createOrder().andReturn().getResponse().getContentAsString()
                .transform(b -> JsonPath.read(b, "$.id"));

        mvc.perform(auth(post("/api/orders/" + orderId + "/cancel"), playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(auth(post("/api/orders/" + orderId + "/cancel"), playerToken)).andExpect(status().isConflict());
        mvc.perform(auth(post("/api/orders/" + orderId + "/pay"), playerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(GOOD_CARD))
                .andExpect(status().isConflict());
    }

    @Test
    void checkoutValidations() throws Exception {
        // carrito vacío
        createOrder().andExpect(status().isBadRequest());

        // juego retirado de la venta después de añadirlo
        addToCart(hadesId);
        mvc.perform(auth(post("/api/developer/games/" + hadesId + "/unpublish"), devToken)).andExpect(status().isOk());
        createOrder().andExpect(status().isConflict());

        // el pedido de otro usuario no existe para mí
        String other = register("other", "other@test.com", "PLAYER");
        mvc.perform(auth(put("/api/cart/items/" + celesteId), other));
        int otherOrder = mvc.perform(auth(post("/api/orders"), other)).andReturn().getResponse().getContentAsString()
                .transform(b -> JsonPath.read(b, "$.id"));
        mvc.perform(auth(get("/api/orders/" + otherOrder), playerToken)).andExpect(status().isNotFound());

        // tarjeta obligatoria con CREDIT_CARD y formato validado
        mvc.perform(auth(post("/api/orders/" + otherOrder + "/pay"), other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CREDIT_CARD\",\"cardNumber\":\"12ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cardNumber").exists());
    }

    // ------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions createOrder() throws Exception {
        return mvc.perform(auth(post("/api/orders"), playerToken));
    }

    private void addToCart(int gameId) throws Exception {
        mvc.perform(auth(put("/api/cart/items/" + gameId), playerToken)).andExpect(status().isOk());
    }

    private int publishGame(String title, String price, int discount) throws Exception {
        List<Integer> genre = JsonPath.read(mvc.perform(get("/api/genres")).andReturn().getResponse()
                .getContentAsString(), "$[?(@.slug=='indie')].id");
        String created = mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","price":%s,"discountPercent":%d,"genreIds":[%d]}"""
                                .formatted(title, price, discount, genre.getFirst())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");
        mvc.perform(auth(post("/api/developer/games/" + id + "/publish"), devToken)).andExpect(status().isOk());
        return id;
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String register(String username, String email, String accountType) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"password123","accountType":"%s"}"""
                        .formatted(username, email, accountType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
