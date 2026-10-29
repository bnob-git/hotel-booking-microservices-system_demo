package com.hotel.booking.controller;

import com.hotel.booking.kafka.AuditEventProducer;
import com.hotel.booking.kafka.BookingEventProducer;
import com.hotel.booking.service.UserLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "booking.guest.rate-limit-per-minute=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuestBookingRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditEventProducer auditEventProducer;

    @MockBean
    private BookingEventProducer bookingEventProducer;

    @MockBean
    private UserLookupService userLookupService;

    @Test
    void lookupIsRateLimitedPerClientIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/bookings/guest/lookup").param("code", "HBZZZZZZ").param("email", "a@b.co")
                            .header("X-Forwarded-For", "10.0.0.1"))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/bookings/guest/lookup").param("code", "HBZZZZZZ").param("email", "a@b.co")
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isTooManyRequests());

        // A different client is not affected
        mockMvc.perform(get("/api/bookings/guest/lookup").param("code", "HBZZZZZZ").param("email", "a@b.co")
                        .header("X-Forwarded-For", "10.0.0.2"))
                .andExpect(status().isNotFound());
    }
}
