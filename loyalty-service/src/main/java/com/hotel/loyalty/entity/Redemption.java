package com.hotel.loyalty.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "redemptions")
public class Redemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long points;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "booking_reference", length = 100)
    private String bookingReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RedemptionStatus status = RedemptionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    public Redemption() {
    }

    public Redemption(Long userId, long points, BigDecimal discountAmount, String bookingReference) {
        this.userId = userId;
        this.points = points;
        this.discountAmount = discountAmount;
        this.bookingReference = bookingReference;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void markReversed() {
        status = RedemptionStatus.REVERSED;
        reversedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public long getPoints() {
        return points;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public RedemptionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReversedAt() {
        return reversedAt;
    }
}
