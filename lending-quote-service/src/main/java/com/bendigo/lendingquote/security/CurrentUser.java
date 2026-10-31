package com.bendigo.lendingquote.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Who is making the current request, for the audit log. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * The staff member's username from the JWT. With security disabled (dev) this is Spring's anonymous
     * principal, "anonymousUser"; "anonymous" only when there is no security context at all.
     */
    public static String name() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getName)
                .orElse("anonymous");
    }
}
