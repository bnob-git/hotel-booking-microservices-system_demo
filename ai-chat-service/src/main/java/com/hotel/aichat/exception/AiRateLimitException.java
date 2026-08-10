package com.hotel.aichat.exception;

public class AiRateLimitException extends RuntimeException {

    public AiRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
