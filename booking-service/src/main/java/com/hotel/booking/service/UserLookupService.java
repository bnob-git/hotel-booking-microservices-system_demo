package com.hotel.booking.service;

import com.hotel.booking.client.UserClient;
import com.hotel.booking.dto.UserResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class UserLookupService {

    private final UserClient userClient;

    public UserLookupService(UserClient userClient) {
        this.userClient = userClient;
    }

    @Cacheable(value = "users", unless = "#result == null")
    public UserResponse getUserById(Long id) {
        return userClient.getUserById(id);
    }
}
