package me.fit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.util.Objects;

// Odgovor sa currency API-ja, polja prate JSON strukturu vanjskog servisa
@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
@NamedQuery(name = CurrencyResponse.GET_BY_USER_ID,
        query = "select c from CurrencyResponse c where c.user.id = :id order by c.id desc")
public class CurrencyResponse {

    public static final String GET_BY_USER_ID = "CurrencyResponse.getByUserId";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "currency_seq")
    @SequenceGenerator(name = "currency_seq", sequenceName = "currency_seq", allocationSize = 1)
    private Long id;

    @Column(name = "input_value")
    private double value;

    @Column(name = "converted_value")
    private double convertedValue;

    @Column(name = "from_currency")
    public String from;

    @Column(name = "to_currency")
    @JsonProperty("to")
    public String to;

    public double rate;
    public String date;
    public String source;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public CurrencyResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public double getConvertedValue() {
        return convertedValue;
    }

    public void setConvertedValue(double convertedValue) {
        this.convertedValue = convertedValue;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CurrencyResponse that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CurrencyResponse{id=" + id + ", from='" + from + "', to='" + to + "', rate=" + rate + '}';
    }
}
