package com.hotel.booking.repository;

import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.PromoCode;
import com.hotel.booking.entity.PromoType;
import com.hotel.booking.entity.Room;
import com.hotel.booking.entity.SeasonalRate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Validates the V10-V12 migrations (ddl-auto=validate) and the seeded pricing reference data. */
@ActiveProfiles("test")
@DataJpaTest
class PricingSchemaRepositoryTest {

    @Autowired private SeasonalRateRepository seasonalRateRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private RoomRepository roomRepository;

    @Test
    void seasonalRatesAreSeeded() {
        List<SeasonalRate> rates = seasonalRateRepository.findByActiveTrue().stream()
                .sorted(Comparator.comparing(SeasonalRate::getCode)).toList();

        assertEquals(2, rates.size());
        assertEquals("PEAK", rates.get(0).getCode());
        assertEquals("12-15", rates.get(0).getStartMonthDay());
        assertEquals("01-05", rates.get(0).getEndMonthDay());
        assertEquals(0, new BigDecimal("1.25").compareTo(rates.get(0).getMultiplier()));
        assertEquals("SUMMER", rates.get(1).getCode());
        assertEquals("07-01", rates.get(1).getStartMonthDay());
        assertEquals("08-31", rates.get(1).getEndMonthDay());
        assertEquals(0, new BigDecimal("1.15").compareTo(rates.get(1).getMultiplier()));
    }

    @Test
    void promoCodesAreSeeded() {
        PromoCode welcome = promoCodeRepository.findByCodeIgnoreCase("welcome10").orElseThrow();
        assertEquals(PromoType.PERCENT, welcome.getType());
        assertEquals(0, new BigDecimal("10").compareTo(welcome.getValue()));

        PromoCode flat = promoCodeRepository.findByCodeIgnoreCase("FLAT25").orElseThrow();
        assertEquals(PromoType.FIXED, flat.getType());
        assertEquals(0, new BigDecimal("100").compareTo(flat.getMinSpend()));

        PromoCode summer = promoCodeRepository.findByCodeIgnoreCase("SUMMER15").orElseThrow();
        assertEquals("07-01", summer.getStayWindowStart());
        assertEquals("08-31", summer.getStayWindowEnd());

        PromoCode expired = promoCodeRepository.findByCodeIgnoreCase("EXPIRED20").orElseThrow();
        assertTrue(expired.getValidTo().isBefore(LocalDate.now()));
    }

    @Test
    void bookingPriceSnapshotIsPersisted() {
        Booking b = new Booking(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 3), 1L, 1L);
        b.setBaseAmount(new BigDecimal("240.00"));
        b.setSeasonalAdjustment(new BigDecimal("0.00"));
        b.setPromoCode("WELCOME10");
        b.setPromoDiscount(new BigDecimal("24.00"));
        b.setTotalPrice(new BigDecimal("216.00"));
        Long id = bookingRepository.saveAndFlush(b).getId();

        Booking loaded = bookingRepository.findById(id).orElseThrow();
        assertEquals(0, new BigDecimal("240.00").compareTo(loaded.getBaseAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(loaded.getSeasonalAdjustment()));
        assertEquals("WELCOME10", loaded.getPromoCode());
        assertEquals(0, new BigDecimal("24.00").compareTo(loaded.getPromoDiscount()));
    }

    @Test
    void roomMaxGuestsIsPersisted() {
        Room room = new Room("Capacity Room", "DELUXE", new BigDecimal("120.00"), true);
        room.setMaxGuests(3);
        Long id = roomRepository.saveAndFlush(room).getId();

        assertEquals(3, roomRepository.findById(id).orElseThrow().getMaxGuests());
    }
}
