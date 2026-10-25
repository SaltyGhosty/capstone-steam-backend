package com.capstone.steam.friend;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.capstone.steam.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Una riga per coppia di utenti, chiunque abbia chiesto l'amicizia (UNIQUE su low/high). */
@Entity
@Table(name = "friendships")
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    /** Il minore dei due id della coppia. */
    @Column(name = "user_low_id", nullable = false)
    private Long userLowId;

    /** Il maggiore dei due id della coppia. */
    @Column(name = "user_high_id", nullable = false)
    private Long userHighId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected Friendship() {
    }

    /** Richiesta pendente da requester ad addressee; calcola l'ordine low/high della coppia. */
    public Friendship(User requester, User addressee) {
        this.requester = requester;
        this.addressee = addressee;
        this.userLowId = Math.min(requester.getId(), addressee.getId());
        this.userHighId = Math.max(requester.getId(), addressee.getId());
    }

    public void accept() {
        this.status = FriendshipStatus.ACCEPTED;
        this.respondedAt = Instant.now();
    }

    public boolean involves(Long userId) {
        return requester.getId().equals(userId) || addressee.getId().equals(userId);
    }

    /** L'altro utente della coppia. */
    public User other(Long userId) {
        return requester.getId().equals(userId) ? addressee : requester;
    }

    public Long getId() {
        return id;
    }

    public User getRequester() {
        return requester;
    }

    public User getAddressee() {
        return addressee;
    }

    public Long getUserLowId() {
        return userLowId;
    }

    public Long getUserHighId() {
        return userHighId;
    }

    public FriendshipStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
