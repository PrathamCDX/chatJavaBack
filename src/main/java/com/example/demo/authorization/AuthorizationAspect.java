package com.example.demo.authorization;

import com.example.demo.security.AppUserPrincipal;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Runs the @RoleFilter and @HeaderFilter checks before the annotated method.
 * Throwing AccessDeniedException hands the request to Spring Security's ExceptionTranslationFilter,
 * which returns 401 for anonymous users and 403 for logged-in ones.
 */
@Aspect
@Component
public class AuthorizationAspect {

    private final ContentHeaderAuthorization contentHeader;

    public AuthorizationAspect(ContentHeaderAuthorization contentHeader) {
        this.contentHeader = contentHeader;
    }

    // Runs before any method annotated with @RoleFilter; Spring passes in the annotation
    @Before("@annotation(roleFilter)")
    public void checkRole(RoleFilter roleFilter) {
        AppUserPrincipal user = currentUser();
        boolean allowed = Arrays.stream(roleFilter.value())
                .anyMatch(required -> user.getRoles().stream().anyMatch(required::equalsIgnoreCase));
        if (!allowed) {
            throw new AccessDeniedException("Missing required role");
        }
    }

    @Before("@annotation(headerFilter)")
    public void checkHeader(HeaderFilter headerFilter) {
        // The header is client-controlled, so it's never enough on its own: require a login too
        currentUser();
        if (!contentHeader.allows(headerFilter.value())) {
            throw new AccessDeniedException("X-content not allowed");
        }
    }

    private AppUserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal user)) {
            throw new AccessDeniedException("Not logged in");
        }
        return user;
    }
}
