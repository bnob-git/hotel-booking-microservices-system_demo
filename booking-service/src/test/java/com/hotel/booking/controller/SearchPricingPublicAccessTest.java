package com.hotel.booking.controller;

import com.hotel.booking.entity.Room;
import com.hotel.booking.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full context with the real SecurityConfig: search and quote endpoints are reachable without a JWT. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SearchPricingPublicAccessTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomRepository roomRepository;

    private Long cheapId;
    // Within November no seed season applies, so prices are deterministic
    private final LocalDate checkIn = nextNovember().plusDays(10);
    private final LocalDate checkOut = checkIn.plusDays(2);

    private static LocalDate nextNovember() {
        LocalDate today = LocalDate.now();
        LocalDate nov = LocalDate.of(today.getYear(), 11, 1);
        return nov.isAfter(today) ? nov : nov.plusYears(1);
    }

    @BeforeEach
    void setUp() {
        Room cheap = new Room("Access Std", "STANDARD", new BigDecimal("60.00"), true);
        cheap.setMaxGuests(2);
        cheapId = roomRepository.save(cheap).getId();
        Room dear = new Room("Access Dlx", "DELUXE", new BigDecimal("120.00"), true);
        dear.setMaxGuests(3);
        roomRepository.save(dear);
    }

    @Test
    void availabilityIsPublicAndSortedByPrice() throws Exception {
        mockMvc.perform(get("/api/rooms/availability")
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Access Std"))
                .andExpect(jsonPath("$[0].type").value("STANDARD"))
                .andExpect(jsonPath("$[0].nightlyBaseRate").value(60.00))
                .andExpect(jsonPath("$[0].nights").value(2))
                .andExpect(jsonPath("$[0].quote.totalPrice").value(120.00))
                .andExpect(jsonPath("$[0].quote.nightly", hasSize(2)))
                .andExpect(jsonPath("$[0].quote.nightly[0].season").value("STANDARD"))
                .andExpect(jsonPath("$[1].name").value("Access Dlx"));
    }

    @Test
    void availabilityAppliesFilters() throws Exception {
        mockMvc.perform(get("/api/rooms/availability")
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString())
                        .param("guests", "3")
                        .param("type", "deluxe")
                        .param("minPrice", "100")
                        .param("maxPrice", "150"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Access Dlx"));
    }

    @Test
    void availabilityRejectsInvalidDates() throws Exception {
        mockMvc.perform(get("/api/rooms/availability")
                        .param("checkIn", checkOut.toString())
                        .param("checkOut", checkIn.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rooms/availability")
                        .param("checkIn", LocalDate.now().minusDays(2).toString())
                        .param("checkOut", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quoteIsPublicAndAppliesSeededPromo() throws Exception {
        mockMvc.perform(post("/api/pricing/quote")
                        .contentType(APPLICATION_JSON)
                        .content(quoteBody("WELCOME10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(cheapId))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.nights").value(2))
                .andExpect(jsonPath("$.baseAmount").value(120.00))
                .andExpect(jsonPath("$.seasonalAdjustment").value(0.00))
                .andExpect(jsonPath("$.promoCode").value("WELCOME10"))
                .andExpect(jsonPath("$.promoApplied").value(true))
                .andExpect(jsonPath("$.promoDiscount").value(12.00))
                .andExpect(jsonPath("$.totalPrice").value(108.00))
                .andExpect(jsonPath("$.nightly[0].date").value(checkIn.toString()))
                .andExpect(jsonPath("$.nightly[0].multiplier").value(1.00));
    }

    @Test
    void quoteWithExpiredPromoIsNotAnError() throws Exception {
        mockMvc.perform(post("/api/pricing/quote")
                        .contentType(APPLICATION_JSON)
                        .content(quoteBody("EXPIRED20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promoApplied").value(false))
                .andExpect(jsonPath("$.promoMessage").value("Promo code EXPIRED20 has expired"))
                .andExpect(jsonPath("$.totalPrice").value(120.00));
    }

    @Test
    void quoteForUnknownRoomIs404() throws Exception {
        mockMvc.perform(post("/api/pricing/quote")
                        .contentType(APPLICATION_JSON)
                        .content("{\"roomId\":999999,\"checkInDate\":\"" + checkIn
                                + "\",\"checkOutDate\":\"" + checkOut + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void bookingsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    private String quoteBody(String promo) {
        return "{\"roomId\":" + cheapId + ",\"checkInDate\":\"" + checkIn + "\",\"checkOutDate\":\"" + checkOut
                + "\",\"promoCode\":\"" + promo + "\"}";
    }
}
