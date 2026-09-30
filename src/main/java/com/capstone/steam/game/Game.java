package com.capstone.steam.game;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.capstone.steam.genre.Genre;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "games")
public class Game {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, unique = true, length = 170)
    private String slug;

    @Column(length = 10000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "developer_id", nullable = false)
    private User developer;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "discount_percent", nullable = false)
    private int discountPercent;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status = GameStatus.DRAFT;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "game_genres",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"))
    private Set<Genre> genres = new HashSet<>();

    // Contadores de solo lectura para JPA: solo cambian con los UPDATE atómicos de GameRepository.
    // Así una edición del juego (save) nunca pisa ventas o valoraciones concurrentes.
    @Column(name = "sales_count", nullable = false, insertable = false, updatable = false)
    private long salesCount;

    @Column(name = "rating_sum", nullable = false, insertable = false, updatable = false)
    private long ratingSum;

    @Column(name = "rating_count", nullable = false, insertable = false, updatable = false)
    private int ratingCount;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Game() {
    }

    public Game(String title, String slug, User developer, BigDecimal price) {
        this.title = title;
        this.slug = slug;
        this.developer = developer;
        this.price = price;
    }

    /** Precio con el descuento aplicado, redondeado a 2 decimales. */
    public BigDecimal getFinalPrice() {
        if (discountPercent == 0) {
            return price;
        }
        return price.multiply(BigDecimal.valueOf(100L - discountPercent))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Media de 1 a 5, o 0 si no tiene reseñas. */
    public double getAverageRating() {
        return ratingCount == 0 ? 0 : (double) ratingSum / ratingCount;
    }

    public boolean isPublished() {
        return status == GameStatus.PUBLISHED;
    }

    public boolean isOwnedBy(Long userId) {
        return developer.getId().equals(userId);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getDeveloper() {
        return developer;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(int discountPercent) {
        this.discountPercent = discountPercent;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public Set<Genre> getGenres() {
        return genres;
    }

    public void setGenres(Set<Genre> genres) {
        this.genres.clear();
        this.genres.addAll(genres);
    }

    public long getSalesCount() {
        return salesCount;
    }

    public long getRatingSum() {
        return ratingSum;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
