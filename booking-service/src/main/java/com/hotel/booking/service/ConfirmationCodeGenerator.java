package com.hotel.booking.service;

import com.hotel.booking.repository.BookingRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Generates short, human-friendly, unique booking references such as {@code HB7K2QXM}. */
@Component
public class ConfirmationCodeGenerator {

    static final String PREFIX = "HB";
    static final int RANDOM_LENGTH = 6;
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final BookingRepository bookingRepository;

    public ConfirmationCodeGenerator(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!bookingRepository.existsByConfirmationCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique confirmation code");
    }

    String randomCode() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
