package com.capstone.steam.friend;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.ForbiddenException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.common.util.LikePatterns;
import com.capstone.steam.friend.dto.FriendRequestResponse;
import com.capstone.steam.friend.dto.FriendRequestsResponse;
import com.capstone.steam.friend.dto.FriendResponse;
import com.capstone.steam.friend.dto.Relation;
import com.capstone.steam.friend.dto.ProfileGame;
import com.capstone.steam.friend.dto.PublicProfileResponse;
import com.capstone.steam.friend.dto.SearchHit;
import com.capstone.steam.friend.dto.UserCard;
import com.capstone.steam.library.LibraryRepository;
import com.capstone.steam.user.RoleName;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Amicizie tra giocatori. Partecipano solo gli account giocatore (attivi e senza ruolo DEVELOPER
 * né ADMIN): qualsiasi altro utente è invisibile a questo servizio.
 */
@Service
public class FriendService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 50;
    static final int MAX_SEARCH_RESULTS = 20;
    static final int PROFILE_LIBRARY_LIMIT = 100;

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;
    private final FriendMessageRepository messageRepository;

    public FriendService(FriendshipRepository friendshipRepository, UserRepository userRepository,
            LibraryRepository libraryRepository, FriendMessageRepository messageRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.libraryRepository = libraryRepository;
        this.messageRepository = messageRepository;
    }

    // Ricerca

    @Transactional(readOnly = true)
    public List<SearchHit> search(Long viewerId, String q) {
        String term = q == null ? "" : q.trim();
        if (term.length() < MIN_QUERY_LENGTH) {
            throw new BadRequestException("friend.queryTooShort");
        }
        if (term.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("friend.queryTooLong");
        }
        List<User> found = userRepository.searchPlayers(LikePatterns.contains(term), viewerId,
                PageRequest.of(0, MAX_SEARCH_RESULTS));
        Map<Long, Friendship> byOther = relationshipsOf(viewerId);
        return found.stream().map(user -> {
            Friendship friendship = byOther.get(user.getId());
            return new SearchHit(UserCard.from(user), relation(viewerId, friendship), pendingId(friendship));
        }).toList();
    }

    // Amici e richieste

    @Transactional(readOnly = true)
    public List<FriendResponse> friends(Long userId) {
        Map<Long, Integer> unread = new HashMap<>();
        messageRepository.countUnreadBySender(userId).forEach(u -> unread.put(u.senderId(), (int) u.count()));
        return friendsOf(userId, unread);
    }

    /** {@code unread} per id dell'amico; il profilo pubblico passa una mappa vuota per non rivelare i messaggi di un altro. */
    private List<FriendResponse> friendsOf(Long userId, Map<Long, Integer> unread) {
        return friendshipRepository.findAccepted(userId).stream()
                .filter(f -> isPlayer(f.other(userId)))
                .map(f -> FriendResponse.from(f, userId, unread.getOrDefault(f.other(userId).getId(), 0)))
                .sorted(Comparator.comparing((FriendResponse r) -> r.user().displayName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @Transactional(readOnly = true)
    public FriendRequestsResponse requests(Long userId) {
        return new FriendRequestsResponse(
                friendshipRepository.findIncoming(userId).stream()
                        .filter(f -> isPlayer(f.other(userId)))
                        .map(f -> FriendRequestResponse.from(f, userId)).toList(),
                friendshipRepository.findOutgoing(userId).stream()
                        .filter(f -> isPlayer(f.other(userId)))
                        .map(f -> FriendRequestResponse.from(f, userId)).toList());
    }

    /** Chiede l'amicizia; se l'altro aveva già chiesto a questo utente, accetta quella richiesta (ACCEPTED). */
    @Transactional
    public FriendRequestResponse send(Long requesterId, String username) {
        User requester = userRepository.findWithRolesById(requesterId)
                .orElseThrow(() -> NotFoundException.of("user", requesterId));
        if (!isPlayer(requester)) {
            throw new ForbiddenException("friend.playersOnly");
        }
        String name = username == null ? "" : username.trim();
        User target = userRepository.findWithRolesByUsernameIgnoreCase(name)
                .filter(FriendService::isPlayer)
                .orElseThrow(() -> NotFoundException.of("user", name));
        if (target.getId().equals(requesterId)) {
            throw new BadRequestException("friend.self");
        }

        var existing = friendshipRepository.findByPair(
                Math.min(requesterId, target.getId()), Math.max(requesterId, target.getId()));
        if (existing.isPresent()) {
            Friendship friendship = existing.get();
            if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new ConflictException("friend.alreadyFriends");
            }
            if (friendship.getRequester().getId().equals(requesterId)) {
                throw new ConflictException("friend.alreadyRequested");
            }
            friendship.accept(); // richiesta incrociata: l'altro aveva già chiesto
            return FriendRequestResponse.from(friendship, requesterId);
        }
        // Se due richieste incrociate arrivano insieme, la UNIQUE della coppia fa fallire la seconda (409).
        Friendship saved = friendshipRepository.saveAndFlush(new Friendship(requester, target));
        return FriendRequestResponse.from(saved, requesterId);
    }

    @Transactional
    public FriendRequestResponse accept(Long userId, Long requestId) {
        Friendship friendship = pendingFor(requestId, userId, true);
        friendship.accept();
        return FriendRequestResponse.from(friendship, userId);
    }

    /** Il destinatario rifiuta: si cancella la riga. */
    @Transactional
    public void reject(Long userId, Long requestId) {
        friendshipRepository.delete(pendingFor(requestId, userId, true));
    }

    /** Il richiedente annulla la sua richiesta pendente. */
    @Transactional
    public void cancel(Long userId, Long requestId) {
        pendingFor(requestId, userId, false);
        // DELETE condizionato a PENDING: se l'altro l'ha accettata nel frattempo, 0 righe e 404
        if (friendshipRepository.deletePending(requestId) == 0) {
            throw NotFoundException.of("friendRequest", requestId);
        }
    }

    @Transactional
    public void remove(Long userId, String username) {
        User other = userRepository.findWithRolesByUsernameIgnoreCase(username == null ? "" : username.trim())
                .orElseThrow(() -> new NotFoundException("friend.notFriends"));
        Friendship friendship = friendshipRepository
                .findByPair(Math.min(userId, other.getId()), Math.max(userId, other.getId()))
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new NotFoundException("friend.notFriends"));
        friendshipRepository.delete(friendship);
    }

    /** Richiesta PENDING che questo utente può risolvere: come destinatario o come richiedente. */
    private Friendship pendingFor(Long requestId, Long userId, boolean asAddressee) {
        return friendshipRepository.findById(requestId)
                .filter(f -> f.getStatus() == FriendshipStatus.PENDING)
                .filter(f -> (asAddressee ? f.getAddressee() : f.getRequester()).getId().equals(userId))
                // se uno dei due non è più un giocatore attivo, la richiesta è invisibile
                .filter(f -> isPlayer(f.getRequester()) && isPlayer(f.getAddressee()))
                .orElseThrow(() -> NotFoundException.of("friendRequest", requestId));
    }

    // Profilo

    /**
     * Profilo di un giocatore. Libreria e amici escono solo se chi guarda è l'utente stesso, un amico
     * o il profilo è pubblico; altrimenti sono null. La regola vive qui, non nel frontend.
     */
    @Transactional(readOnly = true)
    public PublicProfileResponse profile(Long viewerId, String username) {
        String name = username == null ? "" : username.trim();
        User target = userRepository.findWithRolesByUsernameIgnoreCase(name)
                .filter(FriendService::isPlayer)
                .orElseThrow(() -> NotFoundException.of("user", name));

        Friendship friendship = null;
        Relation relation = Relation.SELF;
        if (!target.getId().equals(viewerId)) {
            friendship = friendshipRepository.findByPair(
                    Math.min(viewerId, target.getId()), Math.max(viewerId, target.getId())).orElse(null);
            relation = relation(viewerId, friendship);
        }
        boolean visible = relation == Relation.SELF || relation == Relation.FRIENDS || target.isProfilePublic();

        List<ProfileGame> library = null;
        List<FriendResponse> friends = null;
        if (visible) {
            library = libraryRepository.findByUserId(target.getId(), PageRequest.of(0, PROFILE_LIBRARY_LIMIT,
                    Sort.by(Sort.Direction.DESC, "acquiredAt"))).map(ProfileGame::from).getContent();
            friends = friendsOf(target.getId(), Map.of());
        }
        return new PublicProfileResponse(UserCard.from(target), target.getCreatedAt(), target.isProfilePublic(),
                relation, pendingId(friendship), visible, library, friends);
    }

    // Utilità

    /** Account giocatore: attivo, senza disattivazione pendente né anonimizzato, con email verificata e senza ruolo DEVELOPER né ADMIN. */
    static boolean isPlayer(User user) {
        return user.isEnabled() && !user.isPendingDeletion() && !user.isDeleted() && user.isEmailVerified() && !user.hasRole(RoleName.ROLE_DEVELOPER) && !user.hasRole(RoleName.ROLE_ADMIN);
    }

    /** Tutte le relazioni (amici e richieste) di un utente, per id dell'altro. */
    private Map<Long, Friendship> relationshipsOf(Long userId) {
        Map<Long, Friendship> byOther = new HashMap<>();
        friendshipRepository.findAccepted(userId).forEach(f -> byOther.put(f.other(userId).getId(), f));
        friendshipRepository.findIncoming(userId).forEach(f -> byOther.put(f.other(userId).getId(), f));
        friendshipRepository.findOutgoing(userId).forEach(f -> byOther.put(f.other(userId).getId(), f));
        return byOther;
    }

    static Relation relation(Long viewerId, Friendship friendship) {
        if (friendship == null) {
            return Relation.NONE;
        }
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            return Relation.FRIENDS;
        }
        return friendship.getRequester().getId().equals(viewerId) ? Relation.REQUEST_SENT
                : Relation.REQUEST_RECEIVED;
    }

    static Long pendingId(Friendship friendship) {
        return friendship != null && friendship.getStatus() == FriendshipStatus.PENDING ? friendship.getId() : null;
    }
}
