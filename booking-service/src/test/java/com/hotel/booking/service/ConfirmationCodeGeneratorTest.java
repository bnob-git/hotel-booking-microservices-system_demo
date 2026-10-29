package com.hotel.booking.service;

import com.hotel.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ConfirmationCodeGeneratorTest {

    @Test
    void generatesPrefixedCodeFromUnambiguousAlphabet() {
        BookingRepository repo = mock(BookingRepository.class);
        String code = new ConfirmationCodeGenerator(repo).generate();
        assertTrue(code.matches("HB[A-HJ-NP-Z2-9]{6}"), code);
    }

    @Test
    void retriesOnCollision() {
        BookingRepository repo = mock(BookingRepository.class);
        when(repo.existsByConfirmationCode(anyString())).thenReturn(true, true, false);
        new ConfirmationCodeGenerator(repo).generate();
        verify(repo, times(3)).existsByConfirmationCode(anyString());
    }

    @Test
    void failsAfterTooManyCollisions() {
        BookingRepository repo = mock(BookingRepository.class);
        when(repo.existsByConfirmationCode(anyString())).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> new ConfirmationCodeGenerator(repo).generate());
    }
}
