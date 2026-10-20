package com.capstone.steam.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.order.Order;
import com.capstone.steam.order.OrderRepository;
import com.capstone.steam.order.OrderService;
import com.capstone.steam.order.PaymentMethod;
import com.capstone.steam.order.dto.PayRequest;
import com.capstone.steam.order.payment.PaymentGateway;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Por qué los UPDATE atómicos: N transacciones concurrentes de verdad (hilos y commits reales)
 * sobre la misma fila.
 * <ul>
 *   <li>{@code sales_count = sales_count + 1} no pierde ninguna actualización. Un
 *       read-modify-write en Java ({@code game.setSales(game.getSales() + 1)}) sí las perdería.</li>
 *   <li>{@code ... WHERE status = 'PENDING'} deja que exactamente UNA petición pague el pedido.</li>
 * </ul>
 * Usa su propio H2 porque hace commits reales.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:concurrency;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
class AtomicUpdateConcurrencyTest {

    private static final int THREADS = 16;

    @Autowired private TransactionTemplate tx;
    @Autowired private UserRepository users;
    @Autowired private GameRepository games;
    @Autowired private OrderRepository orders;
    @Autowired private OrderService orderService;
    @MockitoSpyBean private PaymentGateway gateway;

    private Long userId;

    private Long gameId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> {
            String suffix = String.valueOf(System.nanoTime());
            User user = users.save(new User("u" + suffix, suffix + "@test.com", "hash"));
            Game game = games.save(new Game("Race", "race-" + suffix, user, new BigDecimal("10.00")));
            Order order = new Order(user);
            order.addItem(game);
            orders.save(order);
            game.setStatus(GameStatus.PUBLISHED);
            userId = user.getId();
            gameId = game.getId();
            orderId = order.getId();
        });
    }

    @Test
    void concurrentSalesIncrementsAreNeverLost() throws Exception {
        List<Integer> results = runConcurrently(() ->
                tx.execute(status -> games.incrementSalesCount(List.of(gameId))));

        assertThat(results).containsOnly(1);
        assertThat(games.findById(gameId).orElseThrow().getSalesCount()).isEqualTo(THREADS);
    }

    @Test
    void exactlyOneConcurrentPaymentWinsTheOrder() throws Exception {
        List<Integer> results = runConcurrently(() ->
                tx.execute(status -> orders.markPaid(orderId, Instant.now())));

        assertThat(results).filteredOn(r -> r == 1).hasSize(1);
        assertThat(results).filteredOn(r -> r == 0).hasSize(THREADS - 1);
    }

    /** D33: el bloqueo pesimista hace que el pedido se cobre una sola vez, no solo que se marque una vez. */
    @Test
    void concurrentPaymentsOfTheSameOrderChargeTheGatewayExactlyOnce() throws Exception {
        PayRequest card = new PayRequest(PaymentMethod.CREDIT_CARD, "4242424242424242");
        List<Integer> results = runConcurrently(() -> {
            try {
                orderService.pay(userId, orderId, card);
                return 200;
            } catch (ConflictException e) {
                return 409;
            }
        });

        assertThat(results).filteredOn(r -> r == 200).hasSize(1);
        assertThat(results).filteredOn(r -> r == 409).hasSize(THREADS - 1);
        verify(gateway, times(1)).charge(any(), any(), any(), any());
    }

    @Test
    void payingSomeoneElsesOrderIsStillNotFound() {
        PayRequest card = new PayRequest(PaymentMethod.CREDIT_CARD, "4242424242424242");
        assertThatThrownBy(() -> orderService.pay(userId + 9999, orderId, card)).isInstanceOf(NotFoundException.class);
    }

    /** Lanza THREADS tareas que arrancan a la vez (latch) y devuelve sus resultados. */
    private List<Integer> runConcurrently(Callable<Integer> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }
}
