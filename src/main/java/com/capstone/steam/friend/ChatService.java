package com.capstone.steam.friend;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ForbiddenException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.friend.dto.ConversationResponse;
import com.capstone.steam.friend.dto.FriendActivityResponse;
import com.capstone.steam.friend.dto.FriendActivityResponse.IncomingRequest;
import com.capstone.steam.friend.dto.FriendActivityResponse.UnreadFrom;
import com.capstone.steam.friend.dto.MessageResponse;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Chat uno a uno tra giocatori. Scrivere richiede essere amici in quel momento e che l'altro sia un giocatore; leggere lo storico no (gli ex amici
 * e gli account in pausa si leggono ancora; quelli anonimizzati non esistono). Ordine degli errori: friend.self, friend.playersOnly, 404, chat.notFriends, 429.
 */
@Service
public class ChatService {

    static final int DEFAULT_PAGE_SIZE = 30;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_CONVERSATIONS = 50;

    private final FriendMessageRepository messageRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final MessageRateLimiter rateLimiter;
    private final Clock clock;

    public ChatService(FriendMessageRepository messageRepository, FriendshipRepository friendshipRepository,
            UserRepository userRepository, MessageRateLimiter rateLimiter, Clock clock) {
        this.messageRepository = messageRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    public MessageResponse send(Long userId, String username, String body) {
        User me = loadPlayer(userId, username);
        User other = otherPlayer(username);
        // l'amicizia si verifica in questa stessa transazione, subito prima di inserire
        boolean friends = friendshipRepository
                .findByPair(Math.min(userId, other.getId()), Math.max(userId, other.getId()))
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED).isPresent();
        if (!friends) {
            throw new ForbiddenException("chat.notFriends");
        }
        rateLimiter.check(String.valueOf(userId));
        return MessageResponse.from(messageRepository.saveAndFlush(new FriendMessage(me, other, body)), userId);
    }

    /** Dal più nuovo al più vecchio; {@code before} (id) chiede i precedenti a quel messaggio. */
    @Transactional(readOnly = true)
    public List<MessageResponse> messages(Long userId, String username, Long before, int size) {
        loadPlayer(userId, username);
        User other = readableUser(userId, username);
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return messageRepository.findConversation(Math.min(userId, other.getId()), Math.max(userId, other.getId()),
                before == null ? Long.MAX_VALUE : before, PageRequest.of(0, pageSize)).stream()
                .map(m -> MessageResponse.from(m, userId)).toList();
    }

    /** Segna come letti i messaggi che quella persona mi ha inviato; idempotente. */
    @Transactional
    public void markRead(Long userId, String username) {
        loadPlayer(userId, username);
        User other = readableUser(userId, username);
        messageRepository.markRead(userId, other.getId(), clock.instant());
    }

    /**
     * Le novità per il numero di «Amici»: richieste ricevute e messaggi non letti, solo di giocatori visibili.
     * Due query aggregate (richieste e non letti per mittente) più il caricamento dei nomi.
     */
    @Transactional(readOnly = true)
    public FriendActivityResponse activity(Long userId) {
        List<Friendship> incoming = friendshipRepository.findIncoming(userId);
        // una sola query con i ruoli di chi ha inviato la richiesta (evita un caricamento lazy per riga)
        userRepository.findAllWithRolesByIdIn(incoming.stream().map(f -> f.other(userId).getId()).toList());
        List<IncomingRequest> requests = incoming.stream()
                .filter(f -> FriendService.isPlayer(f.other(userId)))
                .map(f -> new IncomingRequest(f.getId(), f.other(userId).getUsername(),
                        f.other(userId).getDisplayName(), f.getCreatedAt()))
                .toList();
        List<UnreadCount> counts = messageRepository.countUnreadBySender(userId);
        Map<Long, User> senders = new HashMap<>();
        userRepository.findAllById(counts.stream().map(UnreadCount::senderId).toList())
                .forEach(u -> senders.put(u.getId(), u));
        List<UnreadFrom> unread = counts.stream()
                .filter(c -> senders.containsKey(c.senderId()))
                .sorted(Comparator.comparing(UnreadCount::lastAt).reversed())
                .map(c -> new UnreadFrom(senders.get(c.senderId()).getUsername(),
                        senders.get(c.senderId()).getDisplayName(), c.count(), c.lastAt()))
                .toList();
        int total = requests.size() + unread.stream().mapToInt(u -> (int) u.count()).sum();
        return new FriendActivityResponse(requests, unread, total);
    }

    /**
     * Casella della chat: prima chi ha messaggi (l'ultimo più recente in alto, anche ex amici), poi gli
     * amici senza messaggi in ordine di nome; solo giocatori visibili e al massimo 50.
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> conversations(Long userId) {
        Map<Long, Long> unread = new HashMap<>();
        messageRepository.countUnreadBySender(userId).forEach(u -> unread.put(u.senderId(), u.count()));
        Map<Long, User> friends = new HashMap<>();
        List<Friendship> accepted = friendshipRepository.findAccepted(userId);
        // ruoli di tutti gli amici in una query (evita un caricamento lazy per amico in isPlayer)
        userRepository.findAllWithRolesByIdIn(accepted.stream().map(f -> f.other(userId).getId()).toList());
        accepted.forEach(f -> friends.put(f.other(userId).getId(), f.other(userId)));

        List<FriendMessage> last = messageRepository.findLastOfEachConversation(userId);
        Set<Long> otherIds = new HashSet<>();
        last.forEach(m -> otherIds.add(otherOf(m, userId)));
        // con i ruoli caricati in blocco si può applicare isPlayer
        Map<Long, User> others = new HashMap<>();
        userRepository.findAllWithRolesByIdIn(otherIds).forEach(u -> others.put(u.getId(), u));

        List<ConversationResponse> result = new ArrayList<>();
        for (FriendMessage m : last) {
            User other = others.get(otherOf(m, userId));
            if (FriendService.isPlayer(other)) {
                result.add(new ConversationResponse(other.getUsername(), other.getDisplayName(), other.getAvatarUrl(),
                        friends.containsKey(other.getId()), unread.getOrDefault(other.getId(), 0L), m.getBody(),
                        m.getCreatedAt()));
            }
        }
        friends.values().stream().filter(u -> !otherIds.contains(u.getId()) && FriendService.isPlayer(u))
                .sorted(Comparator.comparing(User::getDisplayName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .forEach(u -> result.add(new ConversationResponse(u.getUsername(), u.getDisplayName(),
                        u.getAvatarUrl(), true, 0, null, null)));
        return result.size() > MAX_CONVERSATIONS ? List.copyOf(result.subList(0, MAX_CONVERSATIONS)) : result;
    }

    private static Long otherOf(FriendMessage message, Long userId) {
        return message.getSender().getId().equals(userId) ? message.getRecipient().getId() : message.getSender().getId();
    }

    /** Chi chiama: non può essere se stesso (400) e deve essere un giocatore (403). */
    private User loadPlayer(Long userId, String username) {
        User me = userRepository.findWithRolesById(userId).orElseThrow(() -> NotFoundException.of("user", userId));
        if (me.getUsername().equalsIgnoreCase(clean(username))) {
            throw new BadRequestException("friend.self");
        }
        if (!FriendService.isPlayer(me)) {
            throw new ForbiddenException("friend.playersOnly");
        }
        return me;
    }

    /** L'altro: giocatore visibile o 404 (non distingue un account in pausa da uno che non esiste). */
    private User otherPlayer(String username) {
        String name = clean(username);
        return userRepository.findWithRolesByUsernameIgnoreCase(name).filter(FriendService::isPlayer)
                .orElseThrow(() -> NotFoundException.of("user", name));
    }

    /**
     * Per leggere: un giocatore visibile, o qualsiasi account non anonimizzato con cui ci sia uno storico (un account in pausa si
     * legge ancora). Senza storico, un non giocatore risponde 404 come un utente che non esiste (non si possono enumerare gli account).
     */
    private User readableUser(Long userId, String username) {
        String name = clean(username);
        User other = userRepository.findWithRolesByUsernameIgnoreCase(name).filter(u -> !u.isDeleted())
                .orElseThrow(() -> NotFoundException.of("user", name));
        if (!FriendService.isPlayer(other) && !messageRepository.existsByUserLowIdAndUserHighId(
                Math.min(userId, other.getId()), Math.max(userId, other.getId()))) {
            throw NotFoundException.of("user", name);
        }
        return other;
    }

    private static String clean(String username) {
        return username == null ? "" : username.trim();
    }
}
