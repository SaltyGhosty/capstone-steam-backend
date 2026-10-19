package com.capstone.steam.game.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.genre.GenreRepository;
import com.capstone.steam.user.User;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(GameCriteriaRepository.class)
class GameCriteriaRepositoryTest {

    @Autowired private GameCriteriaRepository catalog;
    @Autowired private GameRepository games;
    @Autowired private GenreRepository genres;
    @Autowired private TestEntityManager em;

    private User developer;

    /*
     *  tÃ­tulo             gÃ©neros            precio  dto  final  lanzamiento  media  ventas
     *  Hollow Knight      indie, platformer  15.00    0   15.00  2017-02-24   5.0    3
     *  Hades              action, indie      25.00   40   15.00  2020-09-17   4.0    5
     *  Elden Ring         action, rpg        60.00    0   60.00  2022-02-25   -      10
     *  100% Orange Juice  indie               5.00    0    5.00  (null)       3.0    0
     *  Secret Draft       action (DRAFT)      1.00    0    1.00  2024-01-01   -      0
     */
    @BeforeEach
    void seed() {
        developer = em.persist(new User("studio", "studio@test.com", "hash"));
        Game hollow = publish("Hollow Knight", "15.00", 0, LocalDate.of(2017, 2, 24), "indie", "platformer");
        Game hades = publish("Hades", "25.00", 40, LocalDate.of(2020, 9, 17), "action", "indie");
        Game elden = publish("Elden Ring", "60.00", 0, LocalDate.of(2022, 2, 25), "action", "rpg");
        Game orange = publish("100% Orange Juice", "5.00", 0, null, "indie");
        Game draft = game("Secret Draft", "1.00", 0, LocalDate.of(2024, 1, 1), "action");
        em.persist(draft);
        em.flush();

        games.applyReviewDelta(hollow.getId(), 2, 2);
        games.applyReviewDelta(hades.getId(), 1, 1);
        games.applyReviewDelta(orange.getId(), 0, 1);
        increment(hollow, 3);
        increment(hades, 5);
        increment(elden, 10);
    }

    @Test
    void withoutFiltersReturnsOnlyPublishedNewestFirstWithUndatedLast() {
        assertThat(titles(GameSearchCriteria.empty(), GameSort.NEWEST))
                .containsExactly("Elden Ring", "Hades", "Hollow Knight", "100% Orange Juice");
    }

    @Test
    void textSearchIsCaseInsensitiveAndEscapesWildcards() {
        assertThat(titles(criteria().q("HOLLOW").build(), GameSort.NEWEST)).containsExactly("Hollow Knight");
        // '%' es literal, no comodÃ­n
        assertThat(titles(criteria().q("%").build(), GameSort.NEWEST)).containsExactly("100% Orange Juice");
    }

    @Test
    void genreFilterMatchesAnyOfTheGivenGenresWithoutDuplicates() {
        assertThat(titles(criteria().genres("indie").build(), GameSort.TITLE))
                .containsExactly("100% Orange Juice", "Hades", "Hollow Knight");
        // Elden Ring y Hollow Knight salen una sola vez aunque coincidan varios gÃ©neros de la lista
        assertThat(titles(criteria().genres("rpg", "platformer", "action").build(), GameSort.TITLE))
                .containsExactly("Elden Ring", "Hades", "Hollow Knight");
    }

    @Test
    void priceRangeUsesDiscountedPrice() {
        assertThat(titles(criteria().maxPrice("15").build(), GameSort.TITLE))
                .containsExactly("100% Orange Juice", "Hades", "Hollow Knight");
        assertThat(titles(criteria().minPrice("20").build(), GameSort.TITLE))
                .containsExactly("Elden Ring");
    }

    @Test
    void onSaleAndMinPositivePercentFilters() {
        assertThat(titles(criteria().onSale().build(), GameSort.TITLE)).containsExactly("Hades");
        assertThat(titles(criteria().minPositivePercent(50).build(), GameSort.TITLE)).containsExactly("Hades", "Hollow Knight");
    }

    @Test
    void combinesFilters() {
        assertThat(titles(criteria().genres("indie").maxPrice("10").build(), GameSort.TITLE))
                .containsExactly("100% Orange Juice");
    }

    @Test
    void sortsByFinalPricePositivePercentAndSales() {
        // Hollow Knight y Hades empatan a 15.00: desempata el id
        assertThat(titles(GameSearchCriteria.empty(), GameSort.PRICE_ASC))
                .containsExactly("100% Orange Juice", "Hollow Knight", "Hades", "Elden Ring");
        assertThat(titles(GameSearchCriteria.empty(), GameSort.TOP_RATED))
                .containsExactly("Hollow Knight", "Hades", "100% Orange Juice", "Elden Ring");
        assertThat(titles(GameSearchCriteria.empty(), GameSort.BEST_SELLING))
                .containsExactly("Elden Ring", "Hades", "Hollow Knight", "100% Orange Juice");
    }

    @Test
    void paginatesWithCorrectTotal() {
        Page<Game> second = catalog.search(GameSearchCriteria.empty(), GameSort.TITLE, PageRequest.of(1, 3));
        assertThat(second.getTotalElements()).isEqualTo(4);
        assertThat(second.getTotalPages()).isEqualTo(2);
        assertThat(second.getContent()).extracting(Game::getTitle).containsExactly("Hollow Knight");
    }

    // ------------------------------------------------------------------

    private List<String> titles(GameSearchCriteria criteria, GameSort sort) {
        return catalog.search(criteria, sort, PageRequest.of(0, 50)).getContent().stream().map(Game::getTitle).toList();
    }

    private Game publish(String title, String price, int discount, LocalDate release, String... genreSlugs) {
        Game game = game(title, price, discount, release, genreSlugs);
        game.setStatus(GameStatus.PUBLISHED);
        return em.persist(game);
    }

    private Game game(String title, String price, int discount, LocalDate release, String... genreSlugs) {
        Game game = new Game(title, title.toLowerCase().replaceAll("[^a-z0-9]+", "-"), developer, new BigDecimal(price));
        game.setDiscountPercent(discount);
        game.setReleaseDate(release);
        game.setGenres(new HashSet<>(genres.findBySlugIn(List.of(genreSlugs))));
        return game;
    }

    private void increment(Game game, int times) {
        for (int i = 0; i < times; i++) {
            games.incrementSalesCount(List.of(game.getId()));
        }
    }

    private static CriteriaBuilder criteria() {
        return new CriteriaBuilder();
    }

    /** Builder mÃ­nimo solo para que los tests se lean mejor. */
    private static final class CriteriaBuilder {
        private String q;
        private List<String> genres;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private Boolean onSale;
        private Integer minPositivePercent;

        CriteriaBuilder q(String value) { this.q = value; return this; }
        CriteriaBuilder genres(String... slugs) { this.genres = List.of(slugs); return this; }
        CriteriaBuilder minPrice(String value) { this.minPrice = new BigDecimal(value); return this; }
        CriteriaBuilder maxPrice(String value) { this.maxPrice = new BigDecimal(value); return this; }
        CriteriaBuilder onSale() { this.onSale = true; return this; }
        CriteriaBuilder minPositivePercent(int value) { this.minPositivePercent = value; return this; }

        GameSearchCriteria build() {
            return new GameSearchCriteria(q, genres, minPrice, maxPrice, onSale, null, null, null, minPositivePercent);
        }
    }
}
