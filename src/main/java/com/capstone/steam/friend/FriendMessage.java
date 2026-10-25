package com.capstone.steam.friend;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.capstone.steam.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Un messaggio di chat da mittente a destinatario; sopravvive alla fine dell'amicizia. */
@Entity
@Table(name = "friend_messages")
public class FriendMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /** Il minore dei due id della coppia. */
    @Column(name = "user_low_id", nullable = false)
    private Long userLowId;

    /** Il maggiore dei due id della coppia. */
    @Column(name = "user_high_id", nullable = false)
    private Long userHighId;

    @Column(nullable = false, length = 1000)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Quando lo ha letto il destinatario; null finché non l'ha fatto. */
    @Column(name = "read_at")
    private Instant readAt;

    protected FriendMessage() {
    }

    /** Nuovo messaggio non letto; calcola l'ordine low/high della coppia. */
    public FriendMessage(User sender, User recipient, String body) {
        this.sender = sender;
        this.recipient = recipient;
        this.body = body;
        this.userLowId = Math.min(sender.getId(), recipient.getId());
        this.userHighId = Math.max(sender.getId(), recipient.getId());
    }

    public Long getId() {
        return id;
    }

    public User getSender() {
        return sender;
    }

    public User getRecipient() {
        return recipient;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
