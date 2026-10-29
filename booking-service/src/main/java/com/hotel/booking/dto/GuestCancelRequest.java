package com.hotel.booking.dto;

public class GuestCancelRequest {

    private String code;
    private String email;

    public GuestCancelRequest() {
    }

    public GuestCancelRequest(String code, String email) {
        this.code = code;
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
