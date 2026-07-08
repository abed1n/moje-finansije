package me.fit.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

// Token za resetovanje lozinke: kratko traje i vazi jednom. U bazi se cuva samo hash.
@Entity
@Table(name = "password_reset_tokens")
@NamedQuery(name = PasswordResetToken.GET_BY_HASH,
        query = "select t from PasswordResetToken t where t.tokenHash = :hash")
public class PasswordResetToken {

    public static final String GET_BY_HASH = "PasswordResetToken.getByHash";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "password_reset_seq")
    @SequenceGenerator(name = "password_reset_seq", sequenceName = "password_reset_seq", allocationSize = 1)
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

    public PasswordResetToken() {
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
        if (!(o instanceof PasswordResetToken that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
