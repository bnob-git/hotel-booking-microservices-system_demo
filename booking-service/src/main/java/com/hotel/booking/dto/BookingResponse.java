package com.hotel.booking.dto;

import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class BookingResponse {

    // System identifiers
    private final Long id;
    private final Long userId;
    private final Long roomId;

    // UI-friendly fields (enriched)
    private final String username;
    private final String roomName;

    // Booking data
    private final LocalDate checkInDate;
    private final LocalDate checkOutDate;

    // Lifecycle data
    private final BookingStatus status;
    private final BigDecimal totalPrice;
    private final Integer numGuests;
    private final String confirmationCode;
    private final String specialRequests;
    private final String contactEmail;
    private final LocalDateTime createdAt;

    // Payment & lifecycle data
    private Instant holdExpiresAt;
    private PaymentStatus paymentStatus;
    private String paymentReference;
    private BigDecimal amountPaid;
    private BigDecimal refundAmount;
    private Instant cancelledAt;
    private String cancellationReason;
    private Instant checkedInAt;
    private Instant completedAt;

    public BookingResponse(Long id,
                           Long userId,
                           Long roomId,
                           String username,
                           String roomName,
                           LocalDate checkInDate,
                           LocalDate checkOutDate) {
        this(id, userId, roomId, username, roomName, checkInDate, checkOutDate,
                null, null, null, null, null, null, null);
    }

    public BookingResponse(Long id,
                           Long userId,
                           Long roomId,
                           String username,
                           String roomName,
                           LocalDate checkInDate,
                           LocalDate checkOutDate,
                           BookingStatus status,
                           BigDecimal totalPrice,
                           Integer numGuests,
                           String confirmationCode,
                           String specialRequests,
                           String contactEmail,
                           LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.roomId = roomId;
        this.username = username;
        this.roomName = roomName;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.status = status;
        this.totalPrice = totalPrice;
        this.numGuests = numGuests;
        this.confirmationCode = confirmationCode;
        this.specialRequests = specialRequests;
        this.contactEmail = contactEmail;
        this.createdAt = createdAt;
    }

    // Factory method (clean mapping style)
    public static BookingResponse of(Booking booking,
                                     String username,
                                     String roomName) {
        BookingResponse response = new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getRoomId(),
                username,
                roomName,
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getStatus(),
                booking.getTotalPrice(),
                booking.getNumGuests(),
                booking.getConfirmationCode(),
                booking.getSpecialRequests(),
                booking.getContactEmail(),
                booking.getCreatedAt()
        );
        response.holdExpiresAt = booking.getHoldExpiresAt();
        response.paymentStatus = booking.getPaymentStatus();
        response.paymentReference = booking.getPaymentReference();
        response.amountPaid = booking.getAmountPaid();
        response.refundAmount = booking.getRefundAmount();
        response.cancelledAt = booking.getCancelledAt();
        response.cancellationReason = booking.getCancellationReason();
        response.checkedInAt = booking.getCheckedInAt();
        response.completedAt = booking.getCompletedAt();
        return response;
    }

    // Getters only (immutable DTO)

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public String getUsername() {
        return username;
    }

    public String getRoomName() {
        return roomName;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public Integer getNumGuests() {
        return numGuests;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public String getSpecialRequests() {
        return specialRequests;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
