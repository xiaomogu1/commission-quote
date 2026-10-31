package com.bendigo.lendingquote.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Keycloak puts realm roles in {"realm_access": {"roles": [...]}}, while Spring Security only reads the
 * "scope" claim by default. This maps each realm role to a ROLE_ authority so hasRole(...) works.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<String> roles = Optional.ofNullable(jwt.getClaimAsMap("realm_access"))
                .map(realmAccess -> (Collection<String>) realmAccess.get("roles"))
                .orElse(List.of());
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
