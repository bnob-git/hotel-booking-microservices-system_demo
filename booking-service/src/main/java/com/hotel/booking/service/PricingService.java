package com.hotel.booking.service;

import com.hotel.booking.dto.NightlyRate;
import com.hotel.booking.dto.PriceQuote;
import com.hotel.booking.entity.PromoCode;
import com.hotel.booking.entity.PromoType;
import com.hotel.booking.entity.Room;
import com.hotel.booking.entity.SeasonalRate;
import com.hotel.booking.repository.PromoCodeRepository;
import com.hotel.booking.repository.SeasonalRateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Computes stay prices: per-night seasonal multipliers first, then an optional promo code.
 * Money is BigDecimal, rounded HALF_UP to 2 decimals.
 */
@Service
public class PricingService {

    public static final String CURRENCY = "EUR";
    static final String STANDARD_SEASON = "STANDARD";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter WINDOW_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private final SeasonalRateRepository seasonalRateRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final Clock clock;

    @Autowired
    public PricingService(SeasonalRateRepository seasonalRateRepository, PromoCodeRepository promoCodeRepository) {
        this(seasonalRateRepository, promoCodeRepository, Clock.systemDefaultZone());
    }

    PricingService(SeasonalRateRepository seasonalRateRepository,
                   PromoCodeRepository promoCodeRepository,
                   Clock clock) {
        this.seasonalRateRepository = seasonalRateRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.clock = clock;
    }

    public PriceQuote quote(Room room, LocalDate checkIn, LocalDate checkOut, String promoCode) {
        return quoteAll(List.of(room), checkIn, checkOut, promoCode).get(0);
    }

    /** Quotes several rooms for the same stay, loading rates and the promo code only once. */
    public List<PriceQuote> quoteAll(List<Room> rooms, LocalDate checkIn, LocalDate checkOut, String promoCode) {
        List<SeasonalRate> rates = seasonalRateRepository.findByActiveTrue();
        String code = normalizeCode(promoCode);
        Optional<PromoCode> promo = code == null ? Optional.empty() : promoCodeRepository.findByCodeIgnoreCase(code);

        return rooms.stream()
                .map(room -> quote(room, checkIn, checkOut, rates, code, promo))
                .toList();
    }

    private PriceQuote quote(Room room,
                             LocalDate checkIn,
                             LocalDate checkOut,
                             List<SeasonalRate> rates,
                             String code,
                             Optional<PromoCode> promo) {

        if (room.getPrice() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room has no nightly rate");
        }

        BigDecimal baseRate = money(room.getPrice());
        List<NightlyRate> nightly = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
            Optional<SeasonalRate> season = seasonFor(night, rates);
            BigDecimal multiplier = season.map(SeasonalRate::getMultiplier).orElse(BigDecimal.ONE);
            BigDecimal rate = money(baseRate.multiply(multiplier));
            nightly.add(new NightlyRate(
                    night,
                    baseRate,
                    displayMultiplier(multiplier),
                    rate,
                    season.map(SeasonalRate::getCode).orElse(STANDARD_SEASON)
            ));
            subtotal = subtotal.add(rate);
        }

        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
        BigDecimal baseAmount = money(baseRate.multiply(BigDecimal.valueOf(nights)));
        BigDecimal seasonalAdjustment = money(subtotal.subtract(baseAmount));

        PromoResult promoResult = code == null
                ? PromoResult.none()
                : evaluatePromo(code, promo, subtotal, checkIn, checkOut);

        BigDecimal total = money(subtotal.subtract(promoResult.discount()).max(BigDecimal.ZERO));

        return new PriceQuote(
                room.getId(),
                nights,
                CURRENCY,
                baseAmount,
                seasonalAdjustment,
                code,
                promoResult.discount(),
                promoResult.applied(),
                promoResult.message(),
                total,
                List.copyOf(nightly)
        );
    }

    private PromoResult evaluatePromo(String code,
                                      Optional<PromoCode> promoOpt,
                                      BigDecimal subtotal,
                                      LocalDate checkIn,
                                      LocalDate checkOut) {

        if (promoOpt.isEmpty() || !promoOpt.get().isActive()) {
            return PromoResult.rejected("Promo code " + code + " is not valid");
        }
        PromoCode promo = promoOpt.get();

        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(promo.getValidFrom())) {
            return PromoResult.rejected("Promo code " + code + " is not active yet");
        }
        if (today.isAfter(promo.getValidTo())) {
            return PromoResult.rejected("Promo code " + code + " has expired");
        }

        if (promo.getStayWindowStart() != null && promo.getStayWindowEnd() != null) {
            MonthDay start = parseMonthDay(promo.getStayWindowStart());
            MonthDay end = parseMonthDay(promo.getStayWindowEnd());
            for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
                if (!inWindow(night, start, end)) {
                    return PromoResult.rejected("Promo code " + code + " is only valid for stays between "
                            + formatMonthDay(start) + " and " + formatMonthDay(end));
                }
            }
        }

        if (promo.getMinSpend() != null && subtotal.compareTo(promo.getMinSpend()) < 0) {
            return PromoResult.rejected("Promo code " + code + " requires a minimum spend of €"
                    + money(promo.getMinSpend()).toPlainString());
        }

        BigDecimal discount;
        String description;
        if (promo.getType() == PromoType.PERCENT) {
            discount = money(subtotal.multiply(promo.getValue()).divide(HUNDRED));
            description = promo.getValue().stripTrailingZeros().toPlainString() + "% off";
        } else {
            discount = money(promo.getValue());
            description = "€" + money(promo.getValue()).toPlainString() + " off";
        }
        // A discount can never exceed the amount it is applied to (total never negative).
        discount = discount.min(money(subtotal));

        return new PromoResult(discount, true, "Promo code " + code + " applied: " + description);
    }

    static Optional<SeasonalRate> seasonFor(LocalDate night, List<SeasonalRate> rates) {
        return rates.stream()
                .filter(r -> inWindow(night, parseMonthDay(r.getStartMonthDay()), parseMonthDay(r.getEndMonthDay())))
                .max(Comparator.comparing(SeasonalRate::getMultiplier));
    }

    /** Inclusive month-day window; start after end means the window wraps over the new year. */
    static boolean inWindow(LocalDate date, MonthDay start, MonthDay end) {
        MonthDay day = MonthDay.from(date);
        if (!start.isAfter(end)) {
            return !day.isBefore(start) && !day.isAfter(end);
        }
        return !day.isBefore(start) || !day.isAfter(end);
    }

    static MonthDay parseMonthDay(String value) {
        return MonthDay.of(Integer.parseInt(value.substring(0, 2)), Integer.parseInt(value.substring(3, 5)));
    }

    static String normalizeCode(String promoCode) {
        if (promoCode == null || promoCode.isBlank()) {
            return null;
        }
        return promoCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String formatMonthDay(MonthDay monthDay) {
        return WINDOW_FORMAT.format(monthDay.atYear(2000));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal displayMultiplier(BigDecimal multiplier) {
        BigDecimal stripped = multiplier.stripTrailingZeros();
        return multiplier.setScale(Math.max(2, stripped.scale()), RoundingMode.HALF_UP);
    }

    private record PromoResult(BigDecimal discount, boolean applied, String message) {

        static PromoResult none() {
            return new PromoResult(BigDecimal.ZERO.setScale(2), false, null);
        }

        static PromoResult rejected(String message) {
            return new PromoResult(BigDecimal.ZERO.setScale(2), false, message);
        }
    }
}
