package com.capstone.steam.friend;

import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ForbiddenException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.friend.dto.MessageResponse;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Chat uno a uno entre jugadores. Escribir exige ser amigos en ese momento y que el otro sea jugador; leer el historial no (los ex-amigos
 * y las cuentas en pausa se siguen leyendo; las anonimizadas no existen). Orden de los errores: friend.self, friend.playersOnly, 404, chat.notFriends, 429.
 */
@Service
public class ChatService {

    static final int DEFAULT_PAGE_SIZE = 30;
    static final int MAX_PAGE_SIZE = 100;

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
        // la amistad se comprueba en esta misma transacción, justo antes de insertar
        boolean friends = friendshipRepository
                .findByPair(Math.min(userId, other.getId()), Math.max(userId, other.getId()))
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED).isPresent();
        if (!friends) {
            throw new ForbiddenException("chat.notFriends");
        }
        rateLimiter.check(String.valueOf(userId));
        return MessageResponse.from(messageRepository.saveAndFlush(new FriendMessage(me, other, body)), userId);
    }

    /** Del más nuevo al más viejo; {@code before} (id) pide los anteriores a ese mensaje. */
    @Transactional(readOnly = true)
    public List<MessageResponse> messages(Long userId, String username, Long before, int size) {
        loadPlayer(userId, username);
        User other = existingUser(username);
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return messageRepository.findConversation(Math.min(userId, other.getId()), Math.max(userId, other.getId()),
                before == null ? Long.MAX_VALUE : before, PageRequest.of(0, pageSize)).stream()
                .map(m -> MessageResponse.from(m, userId)).toList();
    }

    /** Marca como leídos los mensajes que esa persona me envió; idempotente. */
    @Transactional
    public void markRead(Long userId, String username) {
        loadPlayer(userId, username);
        User other = existingUser(username);
        messageRepository.markRead(userId, other.getId(), clock.instant());
    }

    /** Quien llama: no puede ser él mismo (400) y tiene que ser jugador (403). */
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

    /** El otro: jugador visible o 404 (no distingue una cuenta en pausa de una que no existe). */
    private User otherPlayer(String username) {
        String name = clean(username);
        return userRepository.findWithRolesByUsernameIgnoreCase(name).filter(FriendService::isPlayer)
                .orElseThrow(() -> NotFoundException.of("user", name));
    }

    /** Para leer: vale cualquier cuenta que exista y no esté anonimizada (en pausa o desactivada también: se lee su historial). */
    private User existingUser(String username) {
        String name = clean(username);
        return userRepository.findWithRolesByUsernameIgnoreCase(name).filter(u -> !u.isDeleted())
                .orElseThrow(() -> NotFoundException.of("user", name));
    }

    private static String clean(String username) {
        return username == null ? "" : username.trim();
    }
}
