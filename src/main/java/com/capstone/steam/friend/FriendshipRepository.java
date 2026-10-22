package com.capstone.steam.friend;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /** La fila de una pareja (low < high), en cualquier estado. */
    @Query("""
            select f from Friendship f join fetch f.requester join fetch f.addressee
             where f.userLowId = :low and f.userHighId = :high""")
    Optional<Friendship> findByPair(Long low, Long high);

    @Query("""
            select f from Friendship f join fetch f.requester join fetch f.addressee
             where f.status = com.capstone.steam.friend.FriendshipStatus.ACCEPTED
               and (f.requester.id = :userId or f.addressee.id = :userId)""")
    List<Friendship> findAccepted(Long userId);

    @Query("""
            select f from Friendship f join fetch f.requester join fetch f.addressee
             where f.status = com.capstone.steam.friend.FriendshipStatus.PENDING and f.addressee.id = :userId
             order by f.createdAt desc""")
    List<Friendship> findIncoming(Long userId);

    @Query("""
            select f from Friendship f join fetch f.requester join fetch f.addressee
             where f.status = com.capstone.steam.friend.FriendshipStatus.PENDING and f.requester.id = :userId
             order by f.createdAt desc""")
    List<Friendship> findOutgoing(Long userId);

    /** Al anonimizar la cuenta: se borran todas sus amistades y solicitudes, en las dos direcciones. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Friendship f where f.requester.id = :userId or f.addressee.id = :userId")
    int deleteAllOf(Long userId);

    /** Borra solo si sigue PENDING (atómico): una amistad aceptada entre medias no se pierde. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Friendship f where f.id = :id and f.status = com.capstone.steam.friend.FriendshipStatus.PENDING")
    int deletePending(Long id);
}
