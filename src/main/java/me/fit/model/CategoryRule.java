package me.fit.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

// Nauceno pravilo kategorizacije: "opis koji sadrzi PATTERN pripada kategoriji X".
// Nastaje kad korisnik pri uvozu izvoda rucno izabere kategoriju za neki opis.
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uq_rule_user_pattern", columnNames = {"user_id", "pattern"}))
@NamedQuery(name = CategoryRule.GET_BY_USER_ID,
        query = "select r from CategoryRule r where r.user.id = :id")
@NamedQuery(name = CategoryRule.GET_BY_USER_AND_PATTERN,
        query = "select r from CategoryRule r where r.user.id = :id and r.pattern = :pattern")
public class CategoryRule {

    public static final String GET_BY_USER_ID = "CategoryRule.getByUserId";
    public static final String GET_BY_USER_AND_PATTERN = "CategoryRule.getByUserAndPattern";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_seq")
    @SequenceGenerator(name = "rule_seq", sequenceName = "rule_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String pattern;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public CategoryRule() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CategoryRule that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CategoryRule{id=" + id + ", pattern='" + pattern + "'}";
    }
}
