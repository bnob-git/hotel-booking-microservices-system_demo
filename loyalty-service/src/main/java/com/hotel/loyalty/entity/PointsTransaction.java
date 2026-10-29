package com.hotel.loyalty.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ledger entry. {@code points} is signed: positive credits the balance, negative debits it.
 */
@Entity
@Table(name = "points_transactions")
public class PointsTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false)
    private long points;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "confirmation_code", length = 20)
    private String confirmationCode;

    @Column(length = 500)
    private String description;

    @Column(name = "source_event_id", unique = true)
    private UUID sourceEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PointsTransaction() {
    }

    public PointsTransaction(Long userId, TransactionType type, long points, String description) {
        this.userId = userId;
        this.type = type;
        this.points = points;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public TransactionType getType() {
        return type;
    }

    public long getPoints() {
        return points;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public void setConfirmationCode(String confirmationCode) {
        this.confirmationCode = confirmationCode;
    }

    public String getDescription() {
        return description;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(UUID sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
