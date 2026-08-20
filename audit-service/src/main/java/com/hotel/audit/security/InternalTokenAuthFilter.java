package com.hotel.audit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_PATH_PREFIX = "/api/audit/internal";

    private final InternalTokenValidator tokenValidator;

    public InternalTokenAuthFilter(
            @Value("${internal.service-token}") String expectedToken) {
        this.tokenValidator = new InternalTokenValidator(expectedToken);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String providedToken =
                request.getHeader(InternalTokenValidator.HEADER);

        if (!tokenValidator.matches(providedToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
