package me.fit.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

// Refresh token: dugotrajni token kojim se dobija novi kratkotrajni pristupni token.
// U bazi se cuva samo hash, nikad sama vrijednost. Rotira se pri svakom osvjezavanju.
@Entity
@Table(name = "refresh_tokens")
@NamedQuery(name = RefreshToken.GET_BY_HASH,
        query = "select t from RefreshToken t where t.tokenHash = :hash")
public class RefreshToken {

    public static final String GET_BY_HASH = "RefreshToken.getByHash";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "refresh_token_seq")
    @SequenceGenerator(name = "refresh_token_seq", sequenceName = "refresh_token_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public RefreshToken() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RefreshToken that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
