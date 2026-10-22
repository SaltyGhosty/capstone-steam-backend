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

/** Un mensaje de chat de remitente a destinatario; sobrevive a que dejen de ser amigos. */
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

    /** Menor de los dos ids de la pareja. */
    @Column(name = "user_low_id", nullable = false)
    private Long userLowId;

    /** Mayor de los dos ids de la pareja. */
    @Column(name = "user_high_id", nullable = false)
    private Long userHighId;

    @Column(nullable = false, length = 1000)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Cuándo lo leyó el destinatario; null mientras no. */
    @Column(name = "read_at")
    private Instant readAt;

    protected FriendMessage() {
    }

    /** Mensaje nuevo sin leer; calcula el orden low/high de la pareja. */
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
