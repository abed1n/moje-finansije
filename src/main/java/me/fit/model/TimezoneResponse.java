package me.fit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.util.Objects;

// Odgovor sa timeapi.io, polja prate JSON strukturu vanjskog servisa
@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
@NamedQuery(name = TimezoneResponse.GET_BY_USER_ID,
        query = "select t from TimezoneResponse t where t.user.id = :id order by t.id desc")
public class TimezoneResponse {

    public static final String GET_BY_USER_ID = "TimezoneResponse.getByUserId";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "timezone_seq")
    @SequenceGenerator(name = "timezone_seq", sequenceName = "timezone_seq", allocationSize = 1)
    private Long id;

    @Column(name = "tz_year")
    public int year;
    @Column(name = "tz_month")
    public int month;
    @Column(name = "tz_day")
    public int day;
    @Column(name = "tz_hour")
    public int hour;
    @Column(name = "tz_minute")
    public int minute;
    @Column(name = "tz_seconds")
    public int seconds;
    @Column(name = "tz_milliseconds")
    public int milliSeconds;
    @Column(name = "tz_date_time")
    public String dateTime;
    @Column(name = "tz_date")
    public String date;
    @Column(name = "tz_time")
    public String time;
    @Column(name = "tz_time_zone")
    public String timeZone;
    @Column(name = "tz_day_of_week")
    public String dayOfWeek;
    @Column(name = "tz_dst_active")
    public boolean dstActive;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public TimezoneResponse() {
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
        if (!(o instanceof TimezoneResponse that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "TimezoneResponse{id=" + id + ", dateTime='" + dateTime + "', timeZone='" + timeZone + "'}";
    }
}
