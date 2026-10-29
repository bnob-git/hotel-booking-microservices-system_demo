package com.hotel.booking.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BookingEventTest {

    @Test
    void mapsBookingIntoVersionedEventContract() throws Exception {
        Booking booking = new Booking(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 4), 3L, 9L);
        booking.setId(55L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmationCode("HBEVNT01");
        booking.setTotalPrice(new BigDecimal("210.00"));
        booking.setNumGuests(2);
        booking.setContactEmail("guest@example.com");

        BookingEvent event = BookingEvent.of(BookingEventType.BOOKING_CONFIRMED, booking, "bob", "Room 103");

        assertEquals(BookingEvent.SCHEMA_VERSION, event.schemaVersion());
        assertNotNull(event.eventId());
        assertEquals("EUR", event.currency());

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonNode json = mapper.readTree(mapper.writeValueAsString(event));
        assertEquals("BOOKING_CONFIRMED", json.get("eventType").asText());
        assertEquals(55, json.get("bookingId").asLong());
        assertEquals("HBEVNT01", json.get("confirmationCode").asText());
        assertEquals("CONFIRMED", json.get("status").asText());
        assertEquals(9, json.get("userId").asLong());
        assertEquals("guest@example.com", json.get("contactEmail").asText());
        assertEquals(210.00, json.get("totalPrice").asDouble());
        assertEquals(2, json.get("numGuests").asInt());
    }
}
