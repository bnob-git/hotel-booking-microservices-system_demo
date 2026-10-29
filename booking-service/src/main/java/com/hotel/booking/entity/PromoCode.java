package com.hotel.booking.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "promo_codes")
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PromoType type;

    // Percentage (e.g. 10.00 = 10%) for PERCENT, amount in EUR for FIXED
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(nullable = false)
    private LocalDate validFrom;

    @Column(nullable = false)
    private LocalDate validTo;

    @Column(precision = 12, scale = 2)
    private BigDecimal minSpend;

    // Optional recurring stay window ('MM-DD'); every night of the stay must fall inside it
    @Column(length = 5)
    private String stayWindowStart;

    @Column(length = 5)
    private String stayWindowEnd;

    @Column(nullable = false)
    private boolean active = true;

    public PromoCode() {}

    public PromoCode(String code, PromoType type, BigDecimal value, LocalDate validFrom, LocalDate validTo) {
        this.code = code;
        this.type = type;
        this.value = value;
        this.validFrom = validFrom;
        this.validTo = validTo;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PromoType getType() {
        return type;
    }

    public void setType(PromoType type) {
        this.type = type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public BigDecimal getMinSpend() {
        return minSpend;
    }

    public void setMinSpend(BigDecimal minSpend) {
        this.minSpend = minSpend;
    }

    public String getStayWindowStart() {
        return stayWindowStart;
    }

    public void setStayWindowStart(String stayWindowStart) {
        this.stayWindowStart = stayWindowStart;
    }

    public String getStayWindowEnd() {
        return stayWindowEnd;
    }

    public void setStayWindowEnd(String stayWindowEnd) {
        this.stayWindowEnd = stayWindowEnd;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
