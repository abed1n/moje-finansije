package me.fit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.util.Objects;

// Odgovor sa ipapi.co, polja prate JSON strukturu vanjskog servisa
@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
@NamedQuery(name = LocationResponse.GET_BY_USER_ID,
        query = "select l from LocationResponse l where l.user.id = :id order by l.id desc")
public class LocationResponse {

    public static final String GET_BY_USER_ID = "LocationResponse.getByUserId";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "location_seq")
    @SequenceGenerator(name = "location_seq", sequenceName = "location_seq", allocationSize = 1)
    private Long id;

    public String ip;
    public String version;
    public String city;
    public String region;
    public String region_code;
    public String country_code;
    public String country_code_iso3;
    public String country_name;
    public String country_capital;
    public String country_tld;
    public String continent_code;
    public boolean in_eu;
    public String postal;
    public double latitude;
    public double longitude;
    public String timezone;
    public String utc_offset;
    public String country_calling_code;
    public String currency;
    public String currency_name;
    public String languages;
    public double country_area;
    public long country_population;
    public String asn;
    public String org;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public LocationResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LocationResponse that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "LocationResponse{id=" + id + ", ip='" + ip + "', city='" + city + "', country='" + country_name + "'}";
    }
}
