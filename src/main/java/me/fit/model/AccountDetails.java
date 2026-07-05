package me.fit.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.Objects;

@Entity
public class AccountDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_details_seq")
    @SequenceGenerator(name = "account_details_seq", sequenceName = "account_details_seq", allocationSize = 1)
    private Long id;

    private String accountNumber;
    private String bankName;
    private LocalDate openedDate;

    public AccountDetails() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public LocalDate getOpenedDate() {
        return openedDate;
    }

    public void setOpenedDate(LocalDate openedDate) {
        this.openedDate = openedDate;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AccountDetails that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "AccountDetails{id=" + id + ", accountNumber='" + accountNumber + "', bankName='" + bankName + "'}";
    }
}
