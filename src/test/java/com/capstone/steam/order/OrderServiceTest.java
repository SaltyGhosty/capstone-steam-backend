package com.capstone.steam.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.steam.cart.CartItemRepository;
import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.library.LibraryRepository;
import com.capstone.steam.notification.event.OrderPaidEvent;
import com.capstone.steam.notification.event.PaymentFailedEvent;
import com.capstone.steam.order.dto.PayRequest;
import com.capstone.steam.order.payment.PaymentGateway;
import com.capstone.steam.order.payment.PaymentGateway.PaymentResult;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;
import com.capstone.steam.wishlist.WishlistItemRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final long USER_ID = 1L;
    private static final long ORDER_ID = 50L;
    private static final long GAME_ID = 10L;
    private static final PayRequest CARD = new PayRequest(PaymentMethod.CREDIT_CARD, "4242424242424242");

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private WishlistItemRepository wishlistRepository;
    @Mock private LibraryRepository libraryRepository;
    @Mock private GameRepository gameRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private OrderService service;

    private Order order;

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(USER_ID);
        lenient().when(user.getEmail()).thenReturn("player@test.com");
        lenient().when(user.getLocale()).thenReturn("it");

        Game game = new Game("Celeste", "celeste", mock(User.class), new BigDecimal("20.00"));
        ReflectionTestUtils.setField(game, "id", GAME_ID);
        game.setStatus(GameStatus.PUBLISHED);
        game.setCoverUrl("https://img.test/celeste.jpg");

        order = new Order(user);
        order.addItem(game);
        lenient().when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.findOwnForUpdate(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        lenient().when(libraryRepository.findOwnedGameIds(anyLong(), anyCollection())).thenReturn(Set.of());
        lenient().when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentGateway.newReference()).thenReturn("SIM-1");
    }

    @Test
    void checkoutWithEmptyCartIsRejected() {
        when(cartItemRepository.findByUserIdWithGame(USER_ID)).thenReturn(List.of());
        assertThatThrownBy(() -> service.createFromCart(USER_ID)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void approvedPaymentMarksPaidFulfilsAndPublishesOrderPaid() {
        when(paymentGateway.charge(any(), any(), any(), any())).thenReturn(PaymentResult.ok());
        when(orderRepository.markPaid(eq(ORDER_ID), any())).thenReturn(1);

        var outcome = service.pay(USER_ID, ORDER_ID, CARD);

        assertThat(outcome.approved()).isTrue();
        verify(libraryRepository).save(any());
        verify(gameRepository).incrementSalesCount(List.of(GAME_ID));
        verify(cartItemRepository).deleteByUserIdAndGameIdIn(USER_ID, List.of(GAME_ID));
        verify(wishlistRepository).deleteByUserIdAndGameIdIn(USER_ID, List.of(GAME_ID));

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(OrderPaidEvent.class, e -> {
            assertThat(e.email()).isEqualTo("player@test.com");
            assertThat(e.locale()).isEqualTo("it"); // idioma del usuario, leído dentro de la transacción
            assertThat(e.total()).isEqualByComparingTo("20.00");
            assertThat(e.lines()).singleElement().satisfies(line -> {
                assertThat(line.title()).isEqualTo("Celeste");
                assertThat(line.coverUrl()).isEqualTo("https://img.test/celeste.jpg");
                assertThat(line.unitPrice()).isEqualByComparingTo("20.00");
            });
        });
    }

    @Test
    void declinedPaymentClosesOrderAsFailedWithoutFulfilling() {
        when(paymentGateway.charge(any(), any(), any(), any())).thenReturn(PaymentResult.declined("Tarjeta rechazada"));
        when(orderRepository.closePending(ORDER_ID, OrderStatus.FAILED)).thenReturn(1);

        var outcome = service.pay(USER_ID, ORDER_ID, CARD);

        assertThat(outcome.approved()).isFalse();
        verify(libraryRepository, never()).save(any());
        verify(gameRepository, never()).incrementSalesCount(any());
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(PaymentFailedEvent.class, e -> {
            assertThat(e.reason()).isEqualTo("Tarjeta rechazada");
            assertThat(e.locale()).isEqualTo("it");
        });
    }

    @Test
    void concurrentPaymentThatLosesTheRaceGetsConflictAndDoesNotFulfil() {
        when(paymentGateway.charge(any(), any(), any(), any())).thenReturn(PaymentResult.ok());
        when(orderRepository.markPaid(eq(ORDER_ID), any())).thenReturn(0); // otra petición ya lo pagó

        assertThatThrownBy(() -> service.pay(USER_ID, ORDER_ID, CARD)).isInstanceOf(ConflictException.class);
        verify(libraryRepository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void cannotPayAnOrderThatIsNotPending() {
        ReflectionTestUtils.setField(order, "status", OrderStatus.PAID);

        assertThatThrownBy(() -> service.pay(USER_ID, ORDER_ID, CARD)).isInstanceOf(ConflictException.class);
        verify(paymentGateway, never()).charge(any(), any(), any(), any());
    }

    @Test
    void cannotPayForGamesAlreadyOwnedThroughAnotherOrder() {
        when(libraryRepository.findOwnedGameIds(anyLong(), anyCollection())).thenReturn(Set.of(GAME_ID));

        assertThatThrownBy(() -> service.pay(USER_ID, ORDER_ID, CARD))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Celeste");
        verify(paymentGateway, never()).charge(any(), any(), any(), any());
    }

    @Test
    void cardPaymentRequiresCardNumber() {
        assertThatThrownBy(() -> service.pay(USER_ID, ORDER_ID, new PayRequest(PaymentMethod.CREDIT_CARD, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void anotherUsersOrderLooksLikeItDoesNotExist() {
        assertThatThrownBy(() -> service.get(99L, ORDER_ID))
                .isInstanceOf(com.capstone.steam.common.exception.NotFoundException.class);
    }
}
