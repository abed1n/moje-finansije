package me.fit.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

// Cilj stednje: koliko zelim skupiti i do kada (npr. "Ljetovanje - 1500 EUR do avgusta")
@Entity
@NamedQuery(name = SavingsGoal.GET_BY_USER_ID,
        query = "select g from SavingsGoal g where g.user.id = :id order by g.createdAt")
public class SavingsGoal {

    public static final String GET_BY_USER_ID = "SavingsGoal.getByUserId";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "goal_seq")
    @SequenceGenerator(name = "goal_seq", sequenceName = "goal_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal savedAmount = BigDecimal.ZERO;

    private LocalDate deadline;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // Ako je postavljen, napredak cilja prati stanje ovog racuna umjesto rucnih uplata
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public SavingsGoal() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    public BigDecimal getSavedAmount() {
        return savedAmount;
    }

    public void setSavedAmount(BigDecimal savedAmount) {
        this.savedAmount = savedAmount;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SavingsGoal that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "SavingsGoal{id=" + id + ", name='" + name + "', target=" + targetAmount + '}';
    }
}
