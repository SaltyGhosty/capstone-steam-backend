package com.capstone.steam.friend;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FriendMessageRepository extends JpaRepository<FriendMessage, Long> {

    /** Mensajes de una pareja (low < high), del más nuevo al más viejo; beforeId null = los últimos. */
    @Query("""
            select m from FriendMessage m
             where m.userLowId = :low and m.userHighId = :high
               and (:beforeId is null or m.id < :beforeId)
             order by m.id desc""")
    List<FriendMessage> findConversation(Long low, Long high, Long beforeId, Pageable page);

    /** Si hay algún mensaje entre esa pareja (low < high). */
    boolean existsByUserLowIdAndUserHighId(Long low, Long high);

    /** Marca como leídos (atómico) los que ese remitente envió a este destinatario; idempotente. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FriendMessage m set m.readAt = :now
             where m.recipient.id = :recipientId and m.sender.id = :senderId and m.readAt is null""")
    int markRead(Long recipientId, Long senderId, Instant now);

    /** El último mensaje de cada conversación en la que participa este usuario, del más nuevo al más viejo. */
    @Query("""
            select m from FriendMessage m join fetch m.sender join fetch m.recipient
             where m.id in (select max(m2.id) from FriendMessage m2
                             where m2.userLowId = :userId or m2.userHighId = :userId
                             group by m2.userLowId, m2.userHighId)
             order by m.id desc""")
    List<FriendMessage> findLastOfEachConversation(Long userId);

    /** Sin leer por remitente, solo de jugadores visibles (mismo criterio que FriendService.isPlayer). */
    @Query("""
            select new com.capstone.steam.friend.UnreadCount(m.sender.id, count(m), max(m.createdAt))
              from FriendMessage m join m.sender s
             where m.recipient.id = :recipientId and m.readAt is null
               and s.enabled = true and s.deletionScheduledAt is null and s.deletedAt is null
               and s.emailVerified = true
               and not exists (select r.id from s.roles r
                                where r.name in (com.capstone.steam.user.RoleName.ROLE_DEVELOPER,
                                                 com.capstone.steam.user.RoleName.ROLE_ADMIN))
             group by m.sender.id""")
    List<UnreadCount> countUnreadBySender(Long recipientId);

    /** Al anonimizar la cuenta: se borran sus mensajes, enviados y recibidos. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FriendMessage m where m.sender.id = :userId or m.recipient.id = :userId")
    int deleteAllOf(Long userId);
}
