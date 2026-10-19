package com.capstone.steam.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.capstone.steam.cart.CartItem;
import com.capstone.steam.cart.CartItemRepository;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.genre.GenreRepository;
import com.capstone.steam.library.LibraryEntry;
import com.capstone.steam.library.LibraryRepository;
import com.capstone.steam.order.Order;
import com.capstone.steam.order.OrderRepository;
import com.capstone.steam.order.OrderStatus;
import com.capstone.steam.user.RoleName;
import com.capstone.steam.user.RoleRepository;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Mapeo JPA contra el esquema real de Flyway (ddl-auto=validate) y
 * comportamiento de los UPDATE atÃ³micos.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryIntegrationTest {

    @Autowired private TestEntityManager em;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private GenreRepository genres;
    @Autowired private GameRepository games;
    @Autowired private LibraryRepository library;
    @Autowired private CartItemRepository cart;
    @Autowired private OrderRepository orders;

    private User developer;
    private User player;

    @BeforeEach
    void setUp() {
        developer = new User("devstudio", "Dev@Studio.com", "hash");
        developer.addRole(roles.findByName(RoleName.ROLE_DEVELOPER).orElseThrow());
        em.persist(developer);

        player = new User("player1", "player1@test.com", "hash");
        player.addRole(roles.findByName(RoleName.ROLE_USER).orElseThrow());
        em.persist(player);
    }

    @Test
    void userEmailIsNormalizedAndLoginFindsByUsernameOrEmail() {
        assertThat(developer.getEmail()).isEqualTo("dev@studio.com");
        em.flush();
        em.clear();

        User byEmail = users.findByLogin("DEV@studio.com").orElseThrow();
        User byUsername = users.findByLogin("DevStudio").orElseThrow();

        assertThat(byEmail.getId()).isEqualTo(byUsername.getId());
        assertThat(byEmail.hasRole(RoleName.ROLE_DEVELOPER)).isTrue();
    }

    @Test
    void persistsGameWithGenresAndComputesFinalPrice() {
        Game game = newGame("hades", "20.00");
        game.setDiscountPercent(25);
        game.setGenres(Set.copyOf(genres.findBySlugIn(List.of("action", "indie"))));
        em.persist(game);
        em.flush();
        em.clear();

        Game loaded = games.findBySlug("hades").orElseThrow();
        assertThat(loaded.getGenres()).extracting("slug").containsExactlyInAnyOrder("action", "indie");
        assertThat(loaded.getFinalPrice()).isEqualByComparingTo("15.00");
        assertThat(loaded.getSalesCount()).isZero();
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void incrementSalesCountIsAtomicAndDoesNotBumpVersion() {
        Game a = em.persist(newGame("a", "10.00"));
        Game b = em.persist(newGame("b", "10.00"));

        assertThat(games.incrementSalesCount(List.of(a.getId(), b.getId()))).isEqualTo(2);
        games.incrementSalesCount(List.of(a.getId()));

        Game reloaded = games.findById(a.getId()).orElseThrow();
        assertThat(reloaded.getSalesCount()).isEqualTo(2);
        assertThat(reloaded.getVersion()).isZero();
    }

    @Test
    void savingAGameNeverOverwritesCountersUpdatedConcurrently() {
        Game game = em.persist(newGame("c", "10.00"));
        em.flush();
        games.incrementSalesCount(List.of(game.getId())); // limpia el contexto

        Game edited = games.findById(game.getId()).orElseThrow();
        edited.setTitle("Nuevo tÃ­tulo");
        games.saveAndFlush(edited);
        em.clear();

        assertThat(games.findById(game.getId()).orElseThrow().getSalesCount()).isEqualTo(1);
    }

    @Test
    void applyReviewDeltaHandlesCreateEditAndDelete() {
        Game game = em.persist(newGame("rated", "5.00"));
        em.flush();

        games.applyReviewDelta(game.getId(), 1, 1);   // reseña positiva
        games.applyReviewDelta(game.getId(), 0, 1);   // reseña negativa
        Game afterCreates = games.findById(game.getId()).orElseThrow();
        assertThat(afterCreates.getReviewCount()).isEqualTo(2);
        assertThat(afterCreates.getPositiveCount()).isEqualTo(1);
        assertThat(afterCreates.getPositivePercent()).isEqualTo(50);

        games.applyReviewDelta(game.getId(), 1, 0);   // la negativa pasa a positiva
        assertThat(games.findById(game.getId()).orElseThrow().getPositivePercent()).isEqualTo(100);

        games.applyReviewDelta(game.getId(), -1, -1); // se borra una positiva
        Game afterDelete = games.findById(game.getId()).orElseThrow();
        assertThat(afterDelete.getReviewCount()).isEqualTo(1);
        assertThat(afterDelete.getPositiveCount()).isEqualTo(1);
        assertThat(afterDelete.getPositivePercent()).isEqualTo(100);

        games.applyReviewDelta(game.getId(), -1, -1); // y la otra: sin reseñas no hay división entre 0
        assertThat(games.findById(game.getId()).orElseThrow().getPositivePercent()).isZero();
    }

    @Test
    void bulkDiscountOnlyTouchesPublishedGamesOfThatDeveloper() {
        Game published = newGame("pub", "10.00");
        published.setStatus(GameStatus.PUBLISHED);
        em.persist(published);
        Game draft = em.persist(newGame("draft", "10.00"));
        em.flush();

        int updated = games.bulkDiscountForDeveloper(developer.getId(), 50, GameStatus.PUBLISHED);

        assertThat(updated).isEqualTo(1);
        assertThat(games.findById(published.getId()).orElseThrow().getFinalPrice()).isEqualByComparingTo("5.00");
        assertThat(games.findById(draft.getId()).orElseThrow().getDiscountPercent()).isZero();
    }

    @Test
    void markPaidOnlySucceedsOnceFromPending() {
        Game game = em.persist(newGame("paid", "30.00"));
        Order order = new Order(player);
        order.addItem(game);
        em.persist(order);
        em.flush();

        assertThat(orders.markPaid(order.getId(), Instant.now())).isEqualTo(1);
        assertThat(orders.markPaid(order.getId(), Instant.now())).isZero();
        assertThat(orders.closePending(order.getId(), OrderStatus.FAILED)).isZero();

        Order reloaded = orders.findWithItemsById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloaded.getPaidAt()).isNotNull();
        assertThat(reloaded.getTotal()).isEqualByComparingTo("30.00");
        assertThat(reloaded.getItems()).hasSize(1);
    }

    @Test
    void libraryOwnershipAndCartQueries() {
        Game owned = em.persist(newGame("owned", "10.00"));
        Game notOwned = em.persist(newGame("not-owned", "10.00"));
        em.persist(new LibraryEntry(player, owned, null));
        em.persist(new CartItem(player, notOwned));
        em.flush();
        em.clear();

        assertThat(library.findOwnedGameIds(player.getId(), List.of(owned.getId(), notOwned.getId())))
                .containsExactly(owned.getId());
        assertThat(library.findByUserId(player.getId(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(cart.findByUserIdWithGame(player.getId()))
                .extracting(c -> c.getGame().getSlug()).containsExactly("not-owned");

        assertThat(cart.deleteAllByUserId(player.getId())).isEqualTo(1);
        assertThat(cart.existsByUserIdAndGameId(player.getId(), notOwned.getId())).isFalse();
    }

    private Game newGame(String slug, String price) {
        return new Game(slug.toUpperCase(), slug, developer, new BigDecimal(price));
    }
}
