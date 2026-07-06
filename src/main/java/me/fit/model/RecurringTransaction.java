package me.fit.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

// Pravilo koje jednom mjesecno automatski kreira transakciju (kirija, plata, pretplate...)
@Entity
@NamedQuery(name = RecurringTransaction.GET_BY_USER_ID,
        query = "select r from RecurringTransaction r where r.user.id = :id order by r.dayOfMonth")
@NamedQuery(name = RecurringTransaction.GET_ACTIVE,
        query = "select r from RecurringTransaction r where r.active = true")
public class RecurringTransaction {

    public static final String GET_BY_USER_ID = "RecurringTransaction.getByUserId";
    public static final String GET_ACTIVE = "RecurringTransaction.getActive";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "recurring_seq")
    @SequenceGenerator(name = "recurring_seq", sequenceName = "recurring_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    private String description;

    // Dan u mjesecu kada se transakcija kreira (u kracim mjesecima: posljednji dan)
    @Column(nullable = false)
    private int dayOfMonth;

    @Column(nullable = false)
    private boolean active = true;

    // Mjesec u kome je pravilo posljednji put izvrseno, format "2026-07"
    @Column(length = 7)
    private String lastRun;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public RecurringTransaction() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(int dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getLastRun() {
        return lastRun;
    }

    public void setLastRun(String lastRun) {
        this.lastRun = lastRun;
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
        if (!(o instanceof RecurringTransaction that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "RecurringTransaction{id=" + id + ", amount=" + amount + ", dayOfMonth=" + dayOfMonth + '}';
    }
}
