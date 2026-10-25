package com.capstone.steam.steamstore;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.notification.event.GameOnSaleEvent;
import com.capstone.steam.notification.event.SaleRecipient;
import com.capstone.steam.wishlist.WishlistItemRepository;

/**
 * Recupera da Steam prezzo e sconto dei giochi collegati (con AppID) e li salva.
 * Se un gioco pubblicato va in offerta o la migliora, avvisa via email chi lo ha nella wishlist.
 *
 * <p>Le chiamate a Steam avvengono fuori dalla transazione: una rete lenta non trattiene connessioni del DB.
 */
@Service
public class SteamPriceSyncService {

    private static final Logger log = LoggerFactory.getLogger(SteamPriceSyncService.class);
    private static final String STORE_CURRENCY = "EUR";

    private final GameRepository gameRepository;
    private final WishlistItemRepository wishlistRepository;
    private final SteamStoreClient steam;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public SteamPriceSyncService(GameRepository gameRepository, WishlistItemRepository wishlistRepository,
            SteamStoreClient steam, ApplicationEventPublisher events, TransactionTemplate tx) {
        this.gameRepository = gameRepository;
        this.wishlistRepository = wishlistRepository;
        this.steam = steam;
        this.events = events;
        this.tx = tx;
    }

    public SyncResult sync() {
        List<Integer> appIds = tx.execute(status -> gameRepository.findBySteamAppIdIsNotNull().stream()
                .map(Game::getSteamAppId).toList());
        if (appIds == null || appIds.isEmpty()) {
            return new SyncResult(0, 0, 0, 0);
        }
        Map<Integer, SteamPrice> prices = steam.prices(appIds);
        SyncResult result = tx.execute(status -> apply(prices));
        log.info("Precios de Steam: {} juegos, {} cambios, {} ofertas nuevas, {} avisos", result.checked(),
                result.updated(), result.newSales(), result.notified());
        return result;
    }

    private SyncResult apply(Map<Integer, SteamPrice> prices) {
        Instant now = Instant.now();
        List<Game> games = gameRepository.findBySteamAppIdIsNotNull();
        int updated = 0;
        int newSales = 0;
        int notified = 0;
        for (Game game : games) {
            SteamPrice price = prices.get(game.getSteamAppId());
            if (price == null) {
                continue; // Steam non lo conosce o è gratuito: si lascia com'è
            }
            if (!STORE_CURRENCY.equals(price.currency())) {
                log.warn("Steam devuelve {} para el AppID {}; la tienda solo trabaja en euros", price.currency(),
                        game.getSteamAppId());
                continue;
            }
            boolean changed = price.initial().compareTo(game.getPrice()) != 0
                    || price.discountPercent() != game.getDiscountPercent();
            boolean saleStarted = game.isPublished() && price.discountPercent() > game.getDiscountPercent();
            game.applySteamPrice(price.initial(), price.discountPercent(), now);
            if (changed) {
                updated++;
            }
            if (saleStarted) {
                newSales++;
                notified += announceSale(game);
            }
        }
        return new SyncResult(games.size(), updated, newSales, notified);
    }

    private int announceSale(Game game) {
        List<SaleRecipient> recipients = wishlistRepository.findSaleRecipients(game.getId());
        if (!recipients.isEmpty()) {
            events.publishEvent(new GameOnSaleEvent(game.getId(), game.getTitle(), game.getSlug(), game.getCoverUrl(),
                    game.getPrice(), game.getFinalPrice(), game.getDiscountPercent(), recipients));
        }
        return recipients.size();
    }
}
