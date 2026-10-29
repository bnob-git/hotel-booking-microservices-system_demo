package com.hotel.booking.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "seasonal_rates")
public class SeasonalRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    // 'MM-DD'; when start > end the window wraps over the new year
    @Column(nullable = false, length = 5)
    private String startMonthDay;

    @Column(nullable = false, length = 5)
    private String endMonthDay;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal multiplier;

    @Column(nullable = false)
    private boolean active = true;

    public SeasonalRate() {}

    public SeasonalRate(String code, String name, String startMonthDay, String endMonthDay, BigDecimal multiplier) {
        this.code = code;
        this.name = name;
        this.startMonthDay = startMonthDay;
        this.endMonthDay = endMonthDay;
        this.multiplier = multiplier;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStartMonthDay() {
        return startMonthDay;
    }

    public void setStartMonthDay(String startMonthDay) {
        this.startMonthDay = startMonthDay;
    }

    public String getEndMonthDay() {
        return endMonthDay;
    }

    public void setEndMonthDay(String endMonthDay) {
        this.endMonthDay = endMonthDay;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
