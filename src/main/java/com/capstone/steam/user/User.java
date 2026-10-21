package com.capstone.steam.user;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
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

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Siempre en minúsculas (lo normaliza el setter). */
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

    /** El dueño pulsó el enlace del correo (V12). Separado de {@code enabled}: pendiente no es desactivada. */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /** Idioma de los correos: es, en o it (CHECK ck_users_locale en V5). */
    @Column(name = "locale", nullable = false, length = 5)
    private String locale = "es";

    /** Perfil visible para cualquiera con sesión; si no, solo para los amigos (V9). */
    @Column(name = "profile_public", nullable = false)
    private boolean profilePublic = false;

    /** Cuándo se anonimiza la cuenta; null si no hay baja programada (V13). */
    @Column(name = "deletion_scheduled_at")
    private Instant deletionScheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "deletion_requested_by", length = 10)
    private DeletionRequester deletionRequestedBy;

    /** Cuándo se anonimizó; null mientras la cuenta siga viva. */
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

    /** Baja programada y aún sin anonimizar. */
    public boolean isPendingDeletion() {
        return deletionScheduledAt != null && deletedAt == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Deja la fila sin datos personales. Los nombres siguen cumpliendo NOT NULL y UNIQUE y liberan los originales.
     * Los roles se conservan y el borrado de lo que cuelga del usuario es cosa del servicio.
     */
    public void anonymize(String unusablePasswordHash, Instant now) {
        this.username = "deleted_" + id;
        this.email = "deleted_" + id + "@deleted.invalid";
        this.passwordHash = unusablePasswordHash;
        this.displayName = null;
        this.avatarUrl = null;
        this.enabled = false;
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
