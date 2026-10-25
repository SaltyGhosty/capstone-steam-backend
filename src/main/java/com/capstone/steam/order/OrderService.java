package com.capstone.steam.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.cart.CartItem;
import com.capstone.steam.cart.CartItemRepository;
import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.library.LibraryEntry;
import com.capstone.steam.library.LibraryRepository;
import com.capstone.steam.notification.event.OrderPaidEvent;
import com.capstone.steam.notification.event.PaymentFailedEvent;
import com.capstone.steam.order.dto.OrderResponse;
import com.capstone.steam.order.dto.OrderSummaryResponse;
import com.capstone.steam.order.dto.PayRequest;
import com.capstone.steam.order.payment.PaymentGateway;
import com.capstone.steam.order.payment.PaymentGateway.PaymentResult;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;
import com.capstone.steam.wishlist.WishlistItemRepository;

/**
 * Checkout in due passi:
 * <ol>
 *   <li>{@link #createFromCart}: congela il carrello in un ordine PENDING con prezzi snapshot.</li>
 *   <li>{@link #pay}: addebita (simulato). Se approvato: PAID, libreria, vendite e pulizia di
 *       carrello/wishlist. Se rifiutato: FAILED, e la transazione fa comunque commit per
 *       lasciare traccia del tentativo (il controller risponde 402).</li>
 * </ol>
 *
 * <p>L'uscita da PENDING usa gli UPDATE atomici di OrderRepository: se due richieste pagano lo
 * stesso ordine insieme, solo una ottiene 1 riga aggiornata; l'altra riceve 409 e fa rollback.
 * Inoltre UNIQUE(user_id, game_id) in library_entries impedisce di avere lo stesso gioco due volte anche se
 * due ordini diversi lo includono.
 *
 * <p>Gli UPDATE atomici svuotano il contesto di persistenza, per questo i dati necessari per
 * l'evento si leggono prima e la risposta finale si rilegge.
 */
@Service
public class OrderService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CartItemRepository cartItemRepository;
    private final WishlistItemRepository wishlistRepository;
    private final LibraryRepository libraryRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orderRepository, PaymentRepository paymentRepository,
            CartItemRepository cartItemRepository, WishlistItemRepository wishlistRepository,
            LibraryRepository libraryRepository, GameRepository gameRepository, UserRepository userRepository,
            PaymentGateway paymentGateway, ApplicationEventPublisher events) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.cartItemRepository = cartItemRepository;
        this.wishlistRepository = wishlistRepository;
        this.libraryRepository = libraryRepository;
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.paymentGateway = paymentGateway;
        this.events = events;
    }

    // Passo 1: carrello -> ordine PENDING

    @Transactional
    public OrderResponse createFromCart(Long userId) {
        List<CartItem> cart = cartItemRepository.findByUserIdWithGame(userId);
        if (cart.isEmpty()) {
            throw new BadRequestException("order.cartEmpty");
        }
        List<Game> games = cart.stream().map(CartItem::getGame).toList();

        List<String> unavailable = games.stream().filter(g -> !g.isPublished()).map(Game::getTitle).toList();
        if (!unavailable.isEmpty()) {
            throw new ConflictException("order.unavailableInCart", String.valueOf(unavailable));
        }
        List<String> owned = titlesOf(games, libraryRepository.findOwnedGameIds(userId, ids(games)));
        if (!owned.isEmpty()) {
            throw new ConflictException("order.ownedInCart", String.valueOf(owned));
        }

        Order order = new Order(userRepository.getReferenceById(userId));
        games.forEach(order::addItem);
        return OrderResponse.from(orderRepository.saveAndFlush(order), null);
    }

    // Passo 2: pagamento

    /** Esito del pagamento: {@code approved} decide tra 200 e 402 nel controller. */
    public record PaymentOutcome(boolean approved, OrderResponse order) {
    }

    @Transactional
    public PaymentOutcome pay(Long userId, Long orderId, PayRequest request) {
        if (request.method() == PaymentMethod.CREDIT_CARD && request.cardNumber() == null) {
            throw new BadRequestException("order.cardRequired");
        }
        // D33: lettura con lock; un pagamento concorrente aspetta qui, vede lo stato finale e dà 409
        // senza chiamare il gateway. Ordine altrui o inesistente: 404 (D31).
        Order order = orderRepository.findOwnForUpdate(orderId, userId)
                .orElseThrow(() -> NotFoundException.of("order", orderId));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("order.notPayable", order.getStatus().name());
        }

        List<Game> games = order.getItems().stream().map(OrderItem::getGame).toList();
        List<Long> gameIds = ids(games);
        List<String> notForSale = games.stream().filter(g -> !g.isPublished()).map(Game::getTitle).toList();
        if (!notForSale.isEmpty()) {
            throw new ConflictException("order.unavailableCancel", String.valueOf(notForSale));
        }
        List<String> alreadyOwned = titlesOf(games, libraryRepository.findOwnedGameIds(userId, gameIds));
        if (!alreadyOwned.isEmpty()) {
            throw new ConflictException("order.ownedCancel", String.valueOf(alreadyOwned));
        }

        // Dati per l'evento, letti prima che gli UPDATE atomici svuotino il contesto
        // (dopo il clear, il proxy LAZY dell'utente non si potrebbe più inizializzare)
        User user = order.getUser();
        String email = user.getEmail();
        String displayName = user.getDisplayName();
        String locale = user.getLocale();
        BigDecimal total = order.getTotal();
        List<OrderPaidEvent.Line> lines = order.getItems().stream()
                .map(item -> new OrderPaidEvent.Line(item.getGame().getTitle(), item.getGame().getCoverUrl(),
                        item.getUnitPrice()))
                .toList();

        Payment payment = paymentRepository.save(new Payment(order, request.method(), paymentGateway.newReference()));
        PaymentResult result = paymentGateway.charge(payment.getProviderRef(), order.getTotal(), request.method(),
                request.cardNumber());

        if (result.approved()) {
            payment.markSucceeded();
            if (orderRepository.markPaid(orderId, Instant.now()) == 0) {
                throw new ConflictException("order.alreadyProcessed");
            }
            fulfil(userId, orderId, gameIds);
            events.publishEvent(new OrderPaidEvent(orderId, userId, email, displayName, total, lines, locale));
        } else {
            payment.markFailed(result.failureReason());
            if (orderRepository.closePending(orderId, OrderStatus.FAILED) == 0) {
                throw new ConflictException("order.alreadyProcessed");
            }
            events.publishEvent(new PaymentFailedEvent(orderId, userId, email, displayName, total,
                    result.failureReason(), locale));
        }
        return new PaymentOutcome(result.approved(), load(orderId));
    }

    /** Consegna: libreria + contatore vendite + pulizia di carrello e wishlist. */
    private void fulfil(Long userId, Long orderId, List<Long> gameIds) {
        User userRef = userRepository.getReferenceById(userId);
        Order orderRef = orderRepository.getReferenceById(orderId);
        gameIds.forEach(gameId -> libraryRepository.save(
                new LibraryEntry(userRef, gameRepository.getReferenceById(gameId), orderRef)));
        libraryRepository.flush(); // qui scatterebbe lo UNIQUE se il gioco è entrato in libreria per un'altra via

        gameRepository.incrementSalesCount(gameIds);
        cartItemRepository.deleteByUserIdAndGameIdIn(userId, gameIds);
        wishlistRepository.deleteByUserIdAndGameIdIn(userId, gameIds);
    }

    @Transactional
    public OrderResponse cancel(Long userId, Long orderId) {
        findOwnOrder(userId, orderId);
        if (orderRepository.closePending(orderId, OrderStatus.CANCELLED) == 0) {
            throw new ConflictException("order.cancelOnlyPending");
        }
        return load(orderId);
    }

    // Consultazioni

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> history(Long userId, Pageable pageable) {
        var page = orderRepository.findByUserId(userId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST));
        return PageResponse.of(page, OrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long userId, Long orderId) {
        findOwnOrder(userId, orderId);
        return load(orderId);
    }

    private OrderResponse load(Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId).orElseThrow(() -> NotFoundException.of("order", orderId));
        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        return OrderResponse.from(order, payments.isEmpty() ? null : payments.getLast());
    }

    /** Un ordine altrui risponde 404, non 403: non si rivela che esiste. */
    private Order findOwnOrder(Long userId, Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> NotFoundException.of("order", orderId));
    }

    private static List<Long> ids(List<Game> games) {
        return games.stream().map(Game::getId).toList();
    }

    private static List<String> titlesOf(List<Game> games, Set<Long> ids) {
        return games.stream().filter(g -> ids.contains(g.getId())).map(Game::getTitle).toList();
    }
}
