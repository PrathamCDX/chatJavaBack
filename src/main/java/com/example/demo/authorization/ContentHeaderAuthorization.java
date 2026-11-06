package com.example.demo.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

/**
 * Checks the X-content header ("static" or "dynamic"); used by
 * AuthorizationAspect for @HeaderFilter.
 * The header is client-controlled, so always combine it with a login check.
 */
@Component
public class ContentHeaderAuthorization {

    public static final String HEADER = "X-content";

    /**
     * True if the current request's X-content header is one of the given values
     * (case-insensitive).
     */
    public boolean allows(String... contentTypes) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String header = request.getHeader(HEADER);
        if (header == null) {
            header = "dynamic";
        }
        return header != null && Set.of(contentTypes).contains(header.trim().toLowerCase());
    }
}
