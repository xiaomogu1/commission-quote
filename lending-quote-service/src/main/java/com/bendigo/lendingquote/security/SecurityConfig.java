package com.bendigo.lendingquote.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;

/**
 * Staff authentication, switched by app.security.enabled.
 *
 * <ul>
 *   <li><b>enabled</b> (stg/prd): the service is an OAuth2 resource server. Every /api call needs a JWT from
 *       the identity provider (Keycloak) carrying the LENDING_STAFF realm role: no token -> 401, wrong role -> 403.</li>
 *   <li><b>disabled</b> (dev, until the UI has a login flow): everything is open.</li>
 * </ul>
 *
 * Only loaded in a web application (filter chains protect HTTP requests).
 * CSRF protection is off in both modes: the API is stateless and authenticates with a bearer token,
 * not a cookie, so there is no session for a cross-site request to ride on.
 */
@Configuration
@ConditionalOnWebApplication
public class SecurityConfig {

    public static final String STAFF_ROLE = "LENDING_STAFF";

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
    SecurityFilterChain securedApi(HttpSecurity http,
                                   @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri)
            throws Exception {
        Assert.hasText(issuerUri, "Security is enabled but no token issuer is configured: set OIDC_ISSUER_URI");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        jwtConverter.setPrincipalClaimName("preferred_username");

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/**").hasRole(STAFF_ROLE)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain openApi(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
