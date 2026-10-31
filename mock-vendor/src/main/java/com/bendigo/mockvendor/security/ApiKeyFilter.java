package com.bendigo.mockvendor.security;

import com.bendigo.mockvendor.config.MockVendorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects any API call that does not carry the expected {@code api-key} header.
 *
 * Only applies to the API (/v1/**), not to actuator health checks. Runs before the controller, so unauthenticated requests never
 * reach business logic. The comparison is constant-time and the key itself is never logged.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "api-key";
    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private final byte[] expectedKey;

    public ApiKeyFilter(MockVendorProperties properties) {
        this.expectedKey = properties.getApiKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(API_KEY_HEADER);
        if (provided == null || !MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected request to {}: {} api-key",
                    request.getRequestURI(), provided == null ? "missing" : "invalid");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"title":"Unauthorized","status":401,"detail":"A valid api-key header is required"}""");
            return;
        }
        chain.doFilter(request, response);
    }
}
