package com.hotel.booking.service;

import com.hotel.booking.dto.NightlyRate;
import com.hotel.booking.dto.PriceQuote;
import com.hotel.booking.entity.PromoCode;
import com.hotel.booking.entity.PromoType;
import com.hotel.booking.entity.Room;
import com.hotel.booking.entity.SeasonalRate;
import com.hotel.booking.repository.PromoCodeRepository;
import com.hotel.booking.repository.SeasonalRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Pricing rules mirror the V10 seed data; the clock is fixed so promo validity is deterministic. */
class PricingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    private PromoCodeRepository promoCodeRepository;
    private PricingService pricingService;
    private Room room;

    @BeforeEach
    void setUp() {
        SeasonalRateRepository seasonalRateRepository = mock(SeasonalRateRepository.class);
        when(seasonalRateRepository.findByActiveTrue()).thenReturn(List.of(
                new SeasonalRate("PEAK", "Peak", "12-15", "01-05", new BigDecimal("1.2500")),
                new SeasonalRate("SUMMER", "Summer", "07-01", "08-31", new BigDecimal("1.1500"))
        ));

        PromoCode welcome = promo("WELCOME10", PromoType.PERCENT, "10.00");
        PromoCode flat = promo("FLAT25", PromoType.FIXED, "25.00");
        flat.setMinSpend(new BigDecimal("100.00"));
        PromoCode summer = promo("SUMMER15", PromoType.PERCENT, "15.00");
        summer.setStayWindowStart("07-01");
        summer.setStayWindowEnd("08-31");
        PromoCode expired = promo("EXPIRED20", PromoType.PERCENT, "20.00");
        expired.setValidTo(LocalDate.of(2025, 1, 1));
        PromoCode future = promo("FUTURE5", PromoType.PERCENT, "5.00");
        future.setValidFrom(LocalDate.of(2027, 1, 1));
        PromoCode inactive = promo("OFF50", PromoType.PERCENT, "50.00");
        inactive.setActive(false);
        PromoCode huge = promo("HUGE500", PromoType.FIXED, "500.00");

        Map<String, PromoCode> promos = Map.of(
                "WELCOME10", welcome, "FLAT25", flat, "SUMMER15", summer, "EXPIRED20", expired,
                "FUTURE5", future, "OFF50", inactive, "HUGE500", huge);

        promoCodeRepository = mock(PromoCodeRepository.class);
        when(promoCodeRepository.findByCodeIgnoreCase(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(promos.get(inv.<String>getArgument(0).toUpperCase())));

        pricingService = new PricingService(seasonalRateRepository, promoCodeRepository,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

        room = new Room("Room 201", "DELUXE", new BigDecimal("100.00"), true);
        room.setId(4L);
    }

    private static PromoCode promo(String code, PromoType type, String value) {
        return new PromoCode(code, type, new BigDecimal(value), LocalDate.of(2024, 1, 1), LocalDate.of(2099, 12, 31));
    }

    private PriceQuote quote(String in, String out, String promo) {
        return pricingService.quote(room, LocalDate.parse(in), LocalDate.parse(out), promo);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
    }

    // ================= SEASONS =================

    @Test
    void standardSeasonUsesBaseRate() {
        PriceQuote q = quote("2026-11-10", "2026-11-13", null);

        assertEquals(3, q.nights());
        assertEquals("EUR", q.currency());
        assertEquals(4L, q.roomId());
        assertMoney("300.00", q.baseAmount());
        assertMoney("0.00", q.seasonalAdjustment());
        assertMoney("300.00", q.totalPrice());
        assertTrue(q.nightly().stream().allMatch(n -> n.season().equals("STANDARD")
                && n.multiplier().compareTo(BigDecimal.ONE) == 0
                && n.rate().compareTo(new BigDecimal("100.00")) == 0));
        assertNull(q.promoCode());
        assertFalse(q.promoApplied());
        assertNull(q.promoMessage());
        assertMoney("0.00", q.promoDiscount());
    }

    @Test
    void peakSeasonAppliesTwentyFivePercent() {
        PriceQuote q = quote("2026-12-20", "2026-12-22", null);

        assertMoney("200.00", q.baseAmount());
        assertMoney("50.00", q.seasonalAdjustment());
        assertMoney("250.00", q.totalPrice());
        assertEquals("PEAK", q.nightly().get(0).season());
        assertMoney("1.25", q.nightly().get(0).multiplier());
        assertMoney("125.00", q.nightly().get(0).rate());
    }

    @Test
    void summerSeasonAppliesFifteenPercent() {
        PriceQuote q = quote("2027-07-10", "2027-07-12", null);

        assertMoney("230.00", q.totalPrice());
        assertMoney("30.00", q.seasonalAdjustment());
        assertTrue(q.nightly().stream().allMatch(n -> n.season().equals("SUMMER")));
    }

    @Test
    void peakSeasonWrapsOverTheNewYearWithInclusiveBounds() {
        PriceQuote q = quote("2026-12-30", "2027-01-07", null);

        List<String> seasons = q.nightly().stream().map(NightlyRate::season).toList();
        // Dec 30, 31, Jan 1..5 are peak; Jan 6 is standard
        assertEquals(List.of("PEAK", "PEAK", "PEAK", "PEAK", "PEAK", "PEAK", "PEAK", "STANDARD"), seasons);
        assertMoney("800.00", q.baseAmount());
        assertMoney("175.00", q.seasonalAdjustment());
        assertMoney("975.00", q.totalPrice());
    }

    @Test
    void peakStartsOnDecemberFifteenth() {
        PriceQuote q = quote("2026-12-14", "2026-12-16", null);

        assertEquals(List.of("STANDARD", "PEAK"), q.nightly().stream().map(NightlyRate::season).toList());
        assertMoney("225.00", q.totalPrice());
    }

    @Test
    void mixedSeasonStayIsPricedPerNight() {
        // Jun 29, Jun 30 standard; Jul 1, Jul 2 summer
        PriceQuote q = quote("2027-06-29", "2027-07-03", null);

        assertEquals(List.of("STANDARD", "STANDARD", "SUMMER", "SUMMER"),
                q.nightly().stream().map(NightlyRate::season).toList());
        assertEquals(LocalDate.of(2027, 6, 29), q.nightly().get(0).date());
        assertMoney("400.00", q.baseAmount());
        assertMoney("30.00", q.seasonalAdjustment());
        assertMoney("430.00", q.totalPrice());
    }

    @Test
    void ratesAreRoundedHalfUpToTwoDecimals() {
        room.setPrice(new BigDecimal("99.99"));

        PriceQuote q = quote("2026-12-20", "2026-12-21", null);

        // 99.99 * 1.25 = 124.9875 -> 124.99
        assertMoney("124.99", q.nightly().get(0).rate());
        assertMoney("124.99", q.totalPrice());
        assertMoney("25.00", q.seasonalAdjustment());
    }

    // ================= PROMO CODES =================

    @Test
    void percentPromoIsAppliedAfterSeasonalAdjustment() {
        PriceQuote q = quote("2026-12-20", "2026-12-22", " welcome10 ");

        assertEquals("WELCOME10", q.promoCode());
        assertTrue(q.promoApplied());
        assertMoney("25.00", q.promoDiscount());
        assertMoney("225.00", q.totalPrice());
        assertEquals("Promo code WELCOME10 applied: 10% off", q.promoMessage());
    }

    @Test
    void fixedPromoAppliesWhenMinimumSpendReached() {
        PriceQuote q = quote("2026-11-10", "2026-11-11", "FLAT25");

        assertTrue(q.promoApplied());
        assertMoney("25.00", q.promoDiscount());
        assertMoney("75.00", q.totalPrice());
    }

    @Test
    void fixedPromoRejectedBelowMinimumSpend() {
        room.setPrice(new BigDecimal("60.00"));

        PriceQuote q = quote("2026-11-10", "2026-11-11", "FLAT25");

        assertFalse(q.promoApplied());
        assertEquals("FLAT25", q.promoCode());
        assertMoney("0.00", q.promoDiscount());
        assertMoney("60.00", q.totalPrice());
        assertEquals("Promo code FLAT25 requires a minimum spend of €100.00", q.promoMessage());
    }

    @Test
    void minimumSpendIsCheckedAgainstSeasonalSubtotal() {
        room.setPrice(new BigDecimal("85.00"));

        // 85 standard < 100, but 85 * 1.25 = 106.25 in peak
        assertFalse(quote("2026-11-10", "2026-11-11", "FLAT25").promoApplied());
        PriceQuote peak = quote("2026-12-20", "2026-12-21", "FLAT25");
        assertTrue(peak.promoApplied());
        assertMoney("81.25", peak.totalPrice());
    }

    @Test
    void expiredPromoIsRejectedWithMessage() {
        PriceQuote q = quote("2026-11-10", "2026-11-12", "EXPIRED20");

        assertFalse(q.promoApplied());
        assertEquals("Promo code EXPIRED20 has expired", q.promoMessage());
        assertMoney("200.00", q.totalPrice());
    }

    @Test
    void notYetValidPromoIsRejected() {
        PriceQuote q = quote("2026-11-10", "2026-11-12", "FUTURE5");

        assertFalse(q.promoApplied());
        assertEquals("Promo code FUTURE5 is not active yet", q.promoMessage());
    }

    @Test
    void unknownAndInactivePromosAreRejected() {
        PriceQuote unknown = quote("2026-11-10", "2026-11-12", "NOPE");
        PriceQuote inactive = quote("2026-11-10", "2026-11-12", "OFF50");

        assertFalse(unknown.promoApplied());
        assertEquals("Promo code NOPE is not valid", unknown.promoMessage());
        assertFalse(inactive.promoApplied());
        assertEquals("Promo code OFF50 is not valid", inactive.promoMessage());
        assertMoney("200.00", inactive.totalPrice());
    }

    @Test
    void stayWindowPromoAppliesOnlyWhenWholeStayIsInsideWindow() {
        PriceQuote inside = quote("2027-08-29", "2027-09-01", "SUMMER15");
        PriceQuote straddling = quote("2027-08-30", "2027-09-02", "SUMMER15");
        PriceQuote outside = quote("2026-11-10", "2026-11-12", "SUMMER15");

        assertTrue(inside.promoApplied());
        // 3 nights * 115 = 345, 15% = 51.75
        assertMoney("51.75", inside.promoDiscount());
        assertMoney("293.25", inside.totalPrice());

        assertFalse(straddling.promoApplied());
        assertEquals("Promo code SUMMER15 is only valid for stays between Jul 1 and Aug 31",
                straddling.promoMessage());
        assertFalse(outside.promoApplied());
    }

    @Test
    void discountNeverMakesTotalNegative() {
        PriceQuote q = quote("2026-11-10", "2026-11-12", "HUGE500");

        assertTrue(q.promoApplied());
        assertMoney("200.00", q.promoDiscount());
        assertMoney("0.00", q.totalPrice());
    }

    @Test
    void blankPromoIsIgnored() {
        PriceQuote q = quote("2026-11-10", "2026-11-12", "   ");

        assertNull(q.promoCode());
        assertNull(q.promoMessage());
        verifyNoInteractions(promoCodeRepository);
    }

    @Test
    void quoteAllLoadsRulesOnce() {
        Room other = new Room("Room 101", "STANDARD", new BigDecimal("60.00"), true);
        other.setId(1L);

        List<PriceQuote> quotes = pricingService.quoteAll(List.of(room, other),
                LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-12"), "WELCOME10");

        assertEquals(2, quotes.size());
        assertMoney("180.00", quotes.get(0).totalPrice());
        assertMoney("108.00", quotes.get(1).totalPrice());
        verify(promoCodeRepository, times(1)).findByCodeIgnoreCase("WELCOME10");
    }

    @Test
    void roomWithoutRateCannotBeQuoted() {
        room.setPrice(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> quote("2026-11-10", "2026-11-12", null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void windowMatchingHandlesWrapAndLeapDay() {
        assertTrue(PricingService.inWindow(LocalDate.of(2028, 2, 29),
                PricingService.parseMonthDay("02-01"), PricingService.parseMonthDay("02-29")));
        assertTrue(PricingService.inWindow(LocalDate.of(2027, 1, 5),
                PricingService.parseMonthDay("12-15"), PricingService.parseMonthDay("01-05")));
        assertFalse(PricingService.inWindow(LocalDate.of(2027, 1, 6),
                PricingService.parseMonthDay("12-15"), PricingService.parseMonthDay("01-05")));
        assertFalse(PricingService.inWindow(LocalDate.of(2026, 12, 14),
                PricingService.parseMonthDay("12-15"), PricingService.parseMonthDay("01-05")));
    }
}
