package com.capstone.steam.friend;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /** La riga di una coppia (low < high), in qualsiasi stato. */
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

    /** All'anonimizzazione dell'account: si cancellano tutte le sue amicizie e richieste, nelle due direzioni. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Friendship f where f.requester.id = :userId or f.addressee.id = :userId")
    int deleteAllOf(Long userId);

    /** Cancella solo se è ancora PENDING (atomico): un'amicizia accettata nel frattempo non si perde. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Friendship f where f.id = :id and f.status = com.capstone.steam.friend.FriendshipStatus.PENDING")
    int deletePending(Long id);
}
