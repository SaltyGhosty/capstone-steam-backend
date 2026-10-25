package com.capstone.steam.user;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * {@code @DynamicUpdate}: ogni UPDATE scrive solo le colonne modificate. Le mutazioni dell'admin caricano la riga
 * senza lock; scrivendo la riga intera, una copia vecchia annullerebbe una cancellazione o un'anonimizzazione confermata tra
 * il caricamento e il flush.
 */
@Entity
@DynamicUpdate
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Sempre in minuscolo (lo normalizza il setter). */
    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Il proprietario ha cliccato il link dell'email (V12). Separato da {@code enabled}: in sospeso non vuol dire disattivato. */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /** Lingua delle email: es, en o it (CHECK ck_users_locale in V5). */
    @Column(name = "locale", nullable = false, length = 5)
    private String locale = "es";

    /** Profilo visibile a chiunque abbia una sessione; altrimenti, solo agli amici (V9). */
    @Column(name = "profile_public", nullable = false)
    private boolean profilePublic = false;

    /** Quando l'account viene anonimizzato; null se non c'è cancellazione programmata (V13). */
    @Column(name = "deletion_scheduled_at")
    private Instant deletionScheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "deletion_requested_by", length = 10)
    private DeletionRequester deletionRequestedBy;

    /** Quando è stato anonimizzato; null finché l'account è vivo. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        setEmail(email);
        this.passwordHash = passwordHash;
        this.displayName = username;
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public boolean hasRole(RoleName roleName) {
        return roles.stream().anyMatch(r -> r.getName() == roleName);
    }

    public Long getId() {
        return id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase();
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public boolean isProfilePublic() {
        return profilePublic;
    }

    public void setProfilePublic(boolean profilePublic) {
        this.profilePublic = profilePublic;
    }

    public Instant getDeletionScheduledAt() {
        return deletionScheduledAt;
    }

    public DeletionRequester getDeletionRequestedBy() {
        return deletionRequestedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void scheduleDeletion(Instant at, DeletionRequester by) {
        this.deletionScheduledAt = at;
        this.deletionRequestedBy = by;
    }

    public void cancelDeletion() {
        this.deletionScheduledAt = null;
        this.deletionRequestedBy = null;
    }

    /** Cancellazione programmata e non ancora anonimizzata. */
    public boolean isPendingDeletion() {
        return deletionScheduledAt != null && deletedAt == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Lascia la riga senza dati personali. I nomi continuano a rispettare NOT NULL e UNIQUE e liberano gli originali.
     * Resta verificato: i {@code deleted_<id>} sono riservati e la pulizia degli account non verificati non li tocca.
     * I ruoli si conservano e la cancellazione di ciò che dipende dall'utente spetta al service.
     */
    public void anonymize(String unusablePasswordHash, Instant now) {
        this.username = "deleted_" + id;
        this.email = "deleted_" + id + "@deleted.invalid";
        this.passwordHash = unusablePasswordHash;
        this.displayName = null;
        this.avatarUrl = null;
        this.enabled = false;
        this.emailVerified = true;
        this.profilePublic = false;
        this.deletedAt = now;
        cancelDeletion();
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
