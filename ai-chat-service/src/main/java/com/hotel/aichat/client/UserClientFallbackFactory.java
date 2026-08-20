package com.hotel.aichat.client;

import com.hotel.aichat.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    private static final Logger log =
            LoggerFactory.getLogger(UserClientFallbackFactory.class);

    @Override
    public UserClient create(Throwable cause) {

        return new UserClient() {

            @Override
            public List<UserResponse> getAllUsers() {
                log.error("User service call failed while loading all users", cause);
                return List.of();
            }

            @Override
            public UserResponse getUserById(Long id) {
                log.error("User service call failed for user {}", id, cause);
                return null;
            }
        };
    }
}
