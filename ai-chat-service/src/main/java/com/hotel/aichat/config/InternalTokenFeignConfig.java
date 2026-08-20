package com.hotel.aichat.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hotel.aichat.security.InternalTokenValidator;

@Configuration
public class InternalTokenFeignConfig {

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor(
            @Value("${internal.service-token}") String serviceToken) {

        String token = InternalTokenValidator.require(serviceToken);

        return template -> template.header(
                InternalTokenValidator.HEADER,
                token
        );
    }
}
