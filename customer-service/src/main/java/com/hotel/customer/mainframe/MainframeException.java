package com.hotel.customer.mainframe;

public class MainframeException extends RuntimeException {

    private final String code;

    public MainframeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public MainframeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
